package com.renomad.minum.utils;

import com.renomad.minum.Constants;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.web.Response;
import com.renomad.minum.web.StatusLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.renomad.minum.utils.Invariants.mustBeTrue;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

/**
 * Helper functions for working with files.
 * <br>
 * In all these functions, note that it is disallowed to request a path
 * having certain characters - see {@link #badFilePathPatterns}
 */
public final class FileUtils {

    /**
     * These patterns can be used in path strings to access files higher in
     * the directory structure.  We disallow this, as a security precaution.
     * <ul>
     * <li>1st Alternative {@code //} - This prevents going to the root directory
     * <li>2nd Alternative {@code ..} - prevents going up a directory
     * <li>3rd Alternative {@code :} - prevents certain special paths, like "C:" or "file://"
     * </ul>
     */
    public static final Pattern badFilePathPatterns = Pattern.compile("//|\\.\\.|:");
    private final ILogger logger;
    private final Constants constants;
    private final Map<String, String> fileSuffixToMime;
    private final Map<String, byte[]> lruCache;
    private final IFileReader fileReader;

    public FileUtils(ILogger logger, Constants constants) {
        this.logger = logger;
        this.constants = constants;
        fileSuffixToMime = new HashMap<>();
        addDefaultValuesForMimeMap();
        readExtraMimeMappings(constants.extraMimeMappings);
        lruCache = LRUCache.getLruCache(constants.maxElementsLruCacheStaticFiles);
        fileReader = new FileReader(lruCache, constants.useCacheForStaticFiles, logger);
    }

    /**
     * This version of the constructor is mainly for testing
     */
    FileUtils(ILogger logger, Constants constants, IFileReader fileReader) {
        this.logger = logger;
        this.constants = constants;
        fileSuffixToMime = new HashMap<>();
        addDefaultValuesForMimeMap();
        readExtraMimeMappings(constants.extraMimeMappings);
        lruCache = LRUCache.getLruCache(constants.maxElementsLruCacheStaticFiles);
        this.fileReader = fileReader;
    }

    /**
     * These are the default starting values for mappings
     * between file suffixes and appropriate mime types
     */
    private void addDefaultValuesForMimeMap() {
        fileSuffixToMime.put("css", "text/css");
        fileSuffixToMime.put("js", "application/javascript");
        fileSuffixToMime.put("webp", "image/webp");
        fileSuffixToMime.put("jpg", "image/jpeg");
        fileSuffixToMime.put("jpeg", "image/jpeg");
        fileSuffixToMime.put("htm", "text/html");
        fileSuffixToMime.put("html", "text/html");
    }

    /**
     * Write a string to a path on disk.
     * <p>
     *     Parent directories are made unavailable by searching the path for
     *     bad characters.  See {@link #badFilePathPatterns}
     * </p>
     */
    public void writeString(Path path, String content) {
        if (path.toString().isEmpty()) {
            logger.logDebug(() -> "an empty path was provided to writeString");
            return;
        }
        if (badFilePathPatterns.matcher(path.toString()).find()) {
            logger.logDebug(() -> String.format("Bad path requested at writeString: %s", path));
            return;
        }
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new UtilsException(e);
        }
    }

    /**
     * Deletes a directory, deleting everything inside it
     * recursively afterwards.  A more dangerous method than
     * many others, take care.
     * <p>
     *     Parent directories are made unavailable by searching the path for
     *     bad characters.  See {@link #badFilePathPatterns}
     * </p>
     */
    public void deleteDirectoryRecursivelyIfExists(Path myPath, ILogger logger) {
        if (badFilePathPatterns.matcher(myPath.toString()).find()) {
            logger.logDebug(() -> String.format("Bad path requested at deleteDirectoryRecursivelyIfExists: %s", myPath));
            return;
        }
        if (!Files.exists(myPath)) {
            logger.logDebug(() -> "system was requested to delete directory: "+myPath+", but it did not exist");
        } else {
            walkPathDeleting(myPath, logger);
        }
    }

    static void walkPathDeleting(Path myPath, ILogger logger) {
        try (Stream<Path> walk = Files.walk(myPath)) {

            final var files = walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile).toList();

            for(var file: files) {
                logger.logDebug(() -> "deleting " + file);
                Files.delete(file.toPath());
            }
        } catch (IOException ex) {
            throw new UtilsException("Error during deleteDirectoryRecursivelyIfExists: " + ex);
        }
    }

    /**
     * Creates a directory if it doesn't already exist.
     * <p>
     *     Parent directories are made unavailable by searching the path for
     *     bad characters.  See {@link #badFilePathPatterns}
     * </p>
     * <p>
     * If the directory does exist, the program will simply skip
     * building it, and mention it in the logs.
     * </p>
     */
    public void makeDirectory(Path directory) {
        if (badFilePathPatterns.matcher(directory.toString()).find()) {
            logger.logDebug(() -> String.format("Bad path requested at makeDirectory: %s", directory));
            return;
        }
        logger.logDebug(() -> "Creating a directory " + directory);
        boolean directoryExists = Files.exists(directory);
        logger.logDebug(() -> "Directory: " + directory + ". Already exists: " + directory);
        if (!directoryExists) {
            logger.logDebug(() -> "Creating directory, since it does not already exist: " + directory);
            innerCreateDirectory(directory);
            logger.logDebug(() -> "Directory: " + directory + " created");
        }
    }

    static void innerCreateDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new UtilsException(e);
        }
    }

    /**
     * Read a binary file, return as a byte array
     * <p>
     *     If there is an error, this will return an empty byte array.
     * </p>
     */
    public byte[] readBinaryFile(String path) {
        try {
            return fileReader.readFile(path);
        } catch (IOException e) {
            logger.logDebug(() -> String.format("Error while reading file %s, returning empty byte array. %s", path, e));
            return new byte[0];
        }
    }

    /**
     * Read a text file from the given path, return as a string.
     *
     * <p>
     *     Access is prevented to data in parent directories or using alternate
     *     drives.  If the data is read, it will be added to a cache, if
     *     the property {@link Constants#useCacheForStaticFiles} is set to true. The maximum
     *     size of the cache is controlled by
     * </p>
     * <p>
     *     If there is an error, this will return an empty string.
     * </p>
     */
    public String readTextFile(String path) {
        try {
            return new String(fileReader.readFile(path));
        } catch (IOException e) {
            logger.logDebug(() -> String.format("Error while reading file %s, returning empty string. %s", path, e));
            return "";
        }
    }

    /**
     * Get a file from a path and create a response for it with a mime type.
     * <p>
     *     Parent directories are made unavailable by searching the path for
     *     bad characters.  See {@link #badFilePathPatterns}
     * </p>
     *
     * @return a response with the file contents and caching headers and mime if valid.
     *  if the path has invalid characters, we'll return a "bad request" response.
     */
    public Response readStaticFile(String path) {
        if (badFilePathPatterns.matcher(path).find()) {
            logger.logDebug(() -> String.format("Bad path requested at readStaticFile: %s", path));
            return new Response(CODE_400_BAD_REQUEST);
        }
        String mimeType = null;

        byte[] fileContents;
        try {
            Path staticFilePath = Path.of(constants.staticFilesDirectory).resolve(path);
            if (!Files.isRegularFile(staticFilePath)) {
                logger.logDebug(() -> String.format("No readable file found at %s", path));
                return new Response(CODE_404_NOT_FOUND);
            }
            fileContents = fileReader.readFile(staticFilePath.toString());
        } catch (IOException e) {
            logger.logAsyncError(() -> String.format("Error while reading file: %s. %s", path, StacktraceUtils.stackTraceToString(e)));
            return new Response(CODE_400_BAD_REQUEST);
        }

        // if the provided path has a dot in it, use that
        // to obtain a suffix for determining file type
        int suffixBeginIndex = path.lastIndexOf('.');
        if (suffixBeginIndex > 0) {
            String suffix = path.substring(suffixBeginIndex+1);
            mimeType = fileSuffixToMime.get(suffix);
        }

        // if we don't find any registered mime types for this
        // suffix, or if it doesn't have a suffix, set the mime type
        // to application/octet-stream
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        return createOkResponseForStaticFiles(fileContents, mimeType);
    }

    /**
     * All static responses will get a cache time of STATIC_FILE_CACHE_TIME seconds
     */
    private Response createOkResponseForStaticFiles(byte[] fileContents, String mimeType) {
        var headers = Map.of(
                "cache-control", "max-age=" + constants.staticFileCacheTime,
                "content-type", mimeType);

        return new Response(
                StatusLine.StatusCode.CODE_200_OK,
                headers,
                fileContents);
    }

    void readExtraMimeMappings(List<String> input) {
        if (input == null || input.isEmpty()) return;
        mustBeTrue(input.size() % 2 == 0, "input must be even (key + value = 2 items). Your input: " + input);

        for (int i = 0; i < input.size(); i += 2) {
            String fileSuffix = input.get(i);
            String mime = input.get(i+1);
            logger.logTrace(() -> "Adding mime mapping: " + fileSuffix + " -> " + mime);
            fileSuffixToMime.put(fileSuffix, mime);
        }
    }

    /**
     * This getter allows users to add extra mappings
     * between file suffixes and mime types, in case
     * a user needs one that was not provided.
     */
    public Map<String,String> getSuffixToMime() {
        return fileSuffixToMime;
    }
}

package com.renomad.minum.utils;

import com.renomad.minum.Constants;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.web.Response;
import com.renomad.minum.web.StatusLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.renomad.minum.utils.Invariants.mustBeTrue;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

/**
 * Helper functions for working with files.
 *
 * In all these functions, note that it is disallowed to request a path
 * having certain characters - see {@link badFilePathPatterns}
 */
public final class FileUtils {

    /**
     * These patterns can be used in path strings to access files higher in
     * the directory structure.  We disallow this, as a security precaution.
     */
    public static final Pattern badFilePathPatterns = Pattern.compile("//|\\.\\.|:");
    private final ILogger logger;
    private final Constants constants;
    private final Map<String, String> fileSuffixToMime;
    private final Map<String, byte[]> lruCache;

    public FileUtils(ILogger logger, Constants constants) {
        this.logger = logger;
        this.constants = constants;
        fileSuffixToMime = new HashMap<>();
        addDefaultValuesForMimeMap();
        readExtraMappings(constants.EXTRA_MIME_MAPPINGS);
        lruCache = LRUCache.getLruCache(1000);
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
     *     bad characters.  See {@link badFilePathPatterns}
     * </p>
     */
    public void writeString(Path path, String content) {
        if (badFilePathPatterns.matcher(path.toString()).find()) {
            logger.logDebug(() -> String.format("Bad path requested at writeString: %s", path.toString()));
            return;
        }
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes a directory, deleting everything inside it
     * recursively afterwards.  A more dangerous method than
     * many others, take care.
     * <p>
     *     Parent directories are made unavailable by searching the path for
     *     bad characters.  See {@link badFilePathPatterns}
     * </p>
     */
    public void deleteDirectoryRecursivelyIfExists(Path myPath, ILogger logger) throws IOException {
        if (badFilePathPatterns.matcher(myPath.toString()).find()) {
            logger.logDebug(() -> String.format("Bad path requested at deleteDirectoryRecursivelyIfExists: %s", myPath.toString()));
            return;
        }
        if (Files.exists(myPath)) {
            try (Stream<Path> walk = Files.walk(myPath)) {

                final var files = walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile).toList();

                for(var file: files) {
                    logger.logDebug(() -> "deleting " + file);
                    final var result = Files.deleteIfExists(file.toPath());
                    if (! result) {
                        logger.logDebug(() -> "failed to delete " + file);
                    }
                }
            }
        }
    }

    /**
     * Creates a directory if it doesn't already exist.
     * <p>
     *     Parent directories are made unavailable by searching the path for
     *     bad characters.  See {@link badFilePathPatterns}
     * </p>
     * If the directory does exist, the program will simply skip
     * building it, and mention it in the logs.
     * @throws IOException needs to get handled.
     */
    public void makeDirectory(Path directory) throws IOException {
        if (badFilePathPatterns.matcher(directory.toString()).find()) {
            logger.logDebug(() -> String.format("Bad path requested at makeDirectory: %s", directory.toString()));
            return;
        }
        logger.logDebug(() -> "Creating a directory " + directory);
        boolean directoryExists = Files.exists(directory);
        logger.logDebug(() -> "Directory: " + directory + ". Already exists: " + directory);
        if (!directoryExists) {
            logger.logDebug(() -> "Creating directory, since it does not already exist: " + directory);
            Files.createDirectories(directory);
            logger.logDebug(() -> "Directory: " + directory + " created");
        }
    }

    private byte[] readFile(String path) throws IOException {
        if (constants.USE_CACHE_FOR_STATIC_FILES && lruCache.containsKey(path)) {
            return lruCache.get(path);
        }

        if (badFilePathPatterns.matcher(path).find()) {
            logger.logDebug(() -> String.format("Bad path requested at makeDirectory: %s", path));
            return new byte[0];
        }

        if (!Files.exists(Path.of(path))) {
            logger.logDebug(() -> String.format("No file found at %s, returning an empty byte array", path));
            return new byte[0];
        }

        try (RandomAccessFile reader = new RandomAccessFile(path, "r");
             FileChannel channel = reader.getChannel();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            int bufferSize = 1024;
            if (bufferSize > channel.size()) {
                bufferSize = (int) channel.size();
            }
            ByteBuffer buff = ByteBuffer.allocate(bufferSize);

            while (channel.read(buff) > 0) {
                out.write(buff.array(), 0, buff.position());
                buff.clear();
            }

            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) {
                logger.logTrace(() -> path + " filesize was 0, returning empty byte array");
                return new byte[0];
            } else {
                String s = path + " filesize was " + bytes.length + " bytes.";
                logger.logTrace(() -> s);

                if (constants.USE_CACHE_FOR_STATIC_FILES) {
                    lruCache.put(path, bytes);
                }
                return bytes;

            }
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
            return readFile(path);
        } catch (IOException e) {
            logger.logDebug(() -> path + String.format("Error while reading file %s, returning empty byte array. %s", path, StacktraceUtils.stackTraceToString(e)));
            return new byte[0];
        }
    }

    /**
     * Read a text file, return as a string.
     * <p>
     *     If there is an error, this will return an empty string.
     * </p>
     */
    public String readTextFile(String path) {
        try {
            return new String(readFile(path));
        } catch (IOException e) {
            logger.logDebug(() -> path + String.format("Error while reading file %s, returning empty string. %s", path, StacktraceUtils.stackTraceToString(e)));
            return "";
        }
    }

    /**
     * Get a file from a path and create a response for it with a mime type.
     * <p>
     *     Parent directories are made unavailable by searching the path for
     *     bad characters.  See {@link badFilePathPatterns}
     * </p>
     *
     * @return a response with the file contents and caching headers and mime if valid.
     *  if the path has invalid characters, we'll return a "bad request" response.
     */
    public Response readStaticFile(String path) {
        if (badFilePathPatterns.matcher(path).find()) {
            logger.logDebug(() -> String.format("Bad path requested at makeDirectory: %s", path));
            return new Response(_400_BAD_REQUEST);
        }
        String mimeType = null;

        byte[] fileContents;
        try {
            Path staticFilePath = Path.of(constants.STATIC_FILES_DIRECTORY).resolve(path);
            if (!Files.exists(staticFilePath)) {
                logger.logDebug(() -> String.format("No file found at %s", path));
                return new Response(_404_NOT_FOUND);
            }
            fileContents = readFile(staticFilePath.toString());
        } catch (IOException e) {
            logger.logAsyncError(() -> String.format("Error while reading file: %s. %s", path, StacktraceUtils.stackTraceToString(e)));
            return new Response(_400_BAD_REQUEST);
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
                "cache-control", "max-age=" + constants.STATIC_FILE_CACHE_TIME,
                "content-type", mimeType);

        return new Response(
                StatusLine.StatusCode._200_OK,
                headers,
                fileContents);
    }

    void readExtraMappings(List<String> input) {
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

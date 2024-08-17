package com.renomad.minum.utils;

import com.renomad.minum.state.Constants;
import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
    private final IFileReader fileReader;

    public FileUtils(ILogger logger, Constants constants) {
        this(
                logger,
                new FileReader(
                        LRUCache.getLruCache(constants.maxElementsLruCacheStaticFiles),
                        constants.useCacheForStaticFiles,
                        logger));
    }

    /**
     * This version of the constructor is mainly for testing
     */
    FileUtils(ILogger logger, IFileReader fileReader) {
        this.logger = logger;
        this.fileReader = fileReader;
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
    public void deleteDirectoryRecursivelyIfExists(Path myPath) {
        if (badFilePathPatterns.matcher(myPath.toString()).find()) {
            logger.logDebug(() -> String.format("Bad path requested at deleteDirectoryRecursivelyIfExists: %s", myPath));
            return;
        }
        if (!Files.exists(myPath)) {
            logger.logDebug(() -> "system was requested to delete directory: "+myPath+", but it did not exist");
        } else {
            walkPathDeleting(myPath);
        }
    }

    void walkPathDeleting(Path myPath) {
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
            return new String(fileReader.readFile(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.logDebug(() -> String.format("Error while reading file %s, returning empty string. %s", path, e));
            return "";
        }
    }

}

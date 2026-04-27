package com.renomad.minum.utils;

import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.state.Constants;
import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Helper functions for working with files.
 */
public final class FileUtils {

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
     * A wrapper around {@link Files#writeString(Path, CharSequence, OpenOption...)}
     * <br>
     * <p>
     *  <em>Note: This does *not* protect against untrusted data on its own.  Call {@link #safeResolve(String, String)} first against
     *  the path to ensure it uses valid characters and prevent it escaping the expected directory.</em>
     * </p>
     * @throws UtilsException if an empty path is provided
     */
    public void writeString(Path path, String content, OpenOption... options) throws IOException {
        if (path.toString().isEmpty()) {
            throw new UtilsException("an empty path was provided to writeString");
        }
        Files.writeString(path, content, options);
    }

    /**
     * A wrapper around {@link Files#readString(Path)}
     * @return the value of the file at the path parameter, as
     * a string, presuming UTF-8 encoding
     * @throws UtilsException if an empty path is provided
     */
    public String readString(Path path) throws IOException {
        if (path.toString().isEmpty()) {
            throw new UtilsException("an empty path was provided to readString");
        }
        return Files.readString(path);
    }

    /**
     * Deletes a directory, deleting everything inside it
     * recursively afterwards.  A more dangerous method than
     * many others, take care.
     * <br>
     * <p>
     *  <em>Note: This does *not* protect against untrusted data on its own.  Call {@link #safeResolve(String, String)} first against
     *  the path to ensure it uses valid characters and prevent it escaping the expected directory.</em>
     * </p>
     */
    public void deleteDirectoryRecursivelyIfExists(Path myPath) throws IOException {
        if (!Files.exists(myPath)) {
            logger.logDebug(() -> "system was requested to delete directory: "+myPath+", but it did not exist");
        } else {
            walkPathDeleting(myPath);
        }
    }

    void walkPathDeleting(Path myPath) throws IOException {
        try (Stream<Path> walk = Files.walk(myPath)) {

            final var files = walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile).toList();

            for (var file : files) {
                logger.logTrace(() -> "deleting " + file);
                Files.delete(file.toPath());
            }
        }
    }

    /**
     * Creates a directory if it doesn't already exist.
     * <br>
     * <p>
     *  <em>Note: This does *not* protect against untrusted data on its own.  Call {@link #safeResolve(String, String)} first against
     *  the path to ensure it uses valid characters and prevent it escaping the expected directory.</em>
     * </p>
     */
    public void makeDirectory(Path directory) throws IOException {
        logger.logDebug(() -> "Creating a directory " + directory);
        boolean directoryExists = Files.exists(directory);
        if (directoryExists) {
            logger.logDebug(() -> "Directory: (" + directory + ") Already exists. Returning.");
        } else {
            innerCreateDirectory(directory);
            logger.logDebug(() -> "Directory: " + directory + " created");
        }
    }

    void innerCreateDirectory(Path directory) throws IOException {
        if (directory == null) throw new IllegalArgumentException("directory parameter is disallowed to be null when creating a directory");
        Files.createDirectories(directory);
    }

    /**
     * Read a binary file, return as a byte array
     * <br>
     * <p>
     *  <em>Note: This does *not* protect against untrusted data on its own.  Call {@link #safeResolve(String, String)} first against
     *  the path to ensure it uses valid characters and prevent it escaping the expected directory.</em>
     * </p>
     * @throws UtilsException as a wrapper around any IOException thrown
     */
    public byte[] readBinaryFile(String path) throws IOException {
        return fileReader.readFile(path);
    }

    /**
     * Read a text file from the given path, return as a string.
     * <br>
     * <p>
     *  <em>Note: This does *not* protect against untrusted data on its own.  Call {@link #safeResolve(String, String)} first against
     *  the path to ensure it uses valid characters and prevent it escaping the expected directory.</em>
     * </p>
     * @throws UtilsException as a wrapper around any IOException thrown
     */
    public String readTextFile(String path) throws IOException {
        return new String(fileReader.readFile(path), StandardCharsets.UTF_8);
    }

    /**
     * This method is to provide assurance that the file specified by the path
     * parameter is within the directory specified by directoryPath.  Use this
     * for any code that reads from files where the user provides untrusted input.
     * @throws ForbiddenUseException if the file is not within the directory
     */
    public void checkFileIsWithinDirectory(String path, String directoryPath) throws IOException {
        Path directoryRealPath;
        Path fullRealPath;
        directoryRealPath = Path.of(directoryPath).toRealPath(LinkOption.NOFOLLOW_LINKS);
        fullRealPath = directoryRealPath.resolve(path).toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (! fullRealPath.startsWith(directoryRealPath)) {
            throw new ForbiddenUseException(String.format("path (%s) was not within directory (%s)", path, directoryPath));
        }
    }

    /**
     * Checks that the path string avoids bad patterns and meets our
     * whitelist for acceptable characters.
     * @throws IllegalArgumentException if the input is blank
     * @throws ForbiddenUseException if the path parameter contains known bad patterns
     *                            or includes characters other than the set of characters we will allow for filenames.
     *                            It is a small set of ascii characters - alphanumerics, underscore, dash, period,
     *                            forward and backward slash.
     */
    public static void checkForBadFilePatterns(String path) {
        if (path.isBlank()) {
            throw new IllegalArgumentException("path was blank");
        }
        char firstChar = path.charAt(0);
        if (firstChar == '\\' || firstChar == '/') {
            throw new ForbiddenUseException("filename ("+path+") contained invalid characters");
        }
        boolean isPreviousCharDot = false;
        boolean isPreviousCharSlash = false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            boolean isWhitelistedChar = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' ||
                    c == '-' || c == '_' || c == '.' || c == '\\' || c == '/';
            if (!isWhitelistedChar) {
                throw new ForbiddenUseException("filename (" + path + ") contained invalid characters (" + c + ").  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash");
            }
            if (c == '.') {
                if (isPreviousCharDot) {
                    throw new ForbiddenUseException("filename ("+path+") contained invalid characters");
                }
                isPreviousCharDot = true;
            } else {
                isPreviousCharDot = false;
            }
            if (c == '/') {
                if (isPreviousCharSlash) {
                    throw new ForbiddenUseException("filename ("+path+") contained invalid characters");
                }
                isPreviousCharSlash = true;
            } else {
                isPreviousCharSlash = false;
            }
        }
    }

    /**
     * This helper method will ensure that the requested path is
     * within the parent directory and using safe characters
     * @throws UtilsException as a wrapper around any IOException thrown
     */
    public Path safeResolve(String parentDirectory, String path) throws IOException {
        checkForBadFilePatterns(path);
        checkFileIsWithinDirectory(path, parentDirectory);
        return Path.of(parentDirectory).resolve(path);
    }

    public void delete(Path path) throws IOException {
        Files.delete(path);
    }

}

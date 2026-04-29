package com.renomad.minum.utils;

import com.renomad.minum.security.ForbiddenUseException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

/**
 * This interface allows us to mock out many of the I/O methods
 * of the framework for easier testing of IOExceptions.
 */
public interface IFileUtils {

    /**
     * A wrapper around {@link Files#writeString(Path, CharSequence, OpenOption...)}
     * <br>
     * <p>
     * <em>Note: This does *not* protect against untrusted data on its own.  Call {@link #safeResolve(String, String)} first against
     * the path to ensure it uses valid characters and prevent it escaping the expected directory.</em>
     * </p>
     *
     * @throws UtilsException if an empty path is provided
     */
    void writeString(Path path, String content, OpenOption... options) throws IOException;

    /**
     * A wrapper around {@link Files#write(Path, Iterable, Charset, OpenOption...)}
     */
    Path write(Path path, Iterable<? extends CharSequence> lines,
               Charset cs, OpenOption... options) throws IOException;

    /**
     * A wrapper around {@link Files#readString(Path)}
     *
     * @return the value of the file at the path parameter, as
     * a string, presuming UTF-8 encoding
     * @throws UtilsException if an empty path is provided
     */
    String readString(Path path) throws IOException;

    /**
     * Deletes a directory, deleting everything inside it
     * recursively afterwards.  A more dangerous method than
     * many others, take care.
     * <br>
     * <p>
     * <em>Note: This does *not* protect against untrusted data on its own.  Call {@link #safeResolve(String, String)} first against
     * the path to ensure it uses valid characters and prevent it escaping the expected directory.</em>
     * </p>
     */
    void deleteDirectoryRecursivelyIfExists(Path myPath) throws IOException;

    /**
     * Creates a directory if it doesn't already exist.
     * <br>
     * <p>
     * <em>Note: This does *not* protect against untrusted data on its own.  Call {@link #safeResolve(String, String)} first against
     * the path to ensure it uses valid characters and prevent it escaping the expected directory.</em>
     * </p>
     */
    void makeDirectory(Path directory) throws IOException;

    /**
     * Read a binary file, return as a byte array
     * <br>
     * <p>
     * <em>Note: This does *not* protect against untrusted data on its own.  Call {@link #safeResolve(String, String)} first against
     * the path to ensure it uses valid characters and prevent it escaping the expected directory.</em>
     * </p>
     *
     */
    byte[] readBinaryFile(String path) throws IOException;

    /**
     * A wrapper around {@link Files#readAllLines(Path)}
     */
    List<String> readAllLines(Path path) throws IOException;

    /**
     * Read a text file from the given path, return as a string.
     * <br>
     * <p>
     * <em>Note: This does *not* protect against untrusted data on its own.  Call {@link #safeResolve(String, String)} first against
     * the path to ensure it uses valid characters and prevent it escaping the expected directory.</em>
     * </p>
     *
     */
    String readTextFile(String path) throws IOException;

    /**
     * This method is to provide assurance that the file specified by the path
     * parameter is within the directory specified by directoryPath.  Use this
     * for any code that reads from files where the user provides untrusted input.
     *
     * @throws ForbiddenUseException if the file is not within the directory
     */
    void checkFileIsWithinDirectory(String path, String directoryPath) throws IOException;

    /**
     * This helper method will ensure that the requested path is
     * within the parent directory and using safe characters
     *
     */
    Path safeResolve(String parentDirectory, String path) throws IOException;

    /**
     * A wrapper around {@link Files#delete(Path)}
     */
    void delete(Path path) throws IOException;

    /**
     * A wrapper around {@link Files#move(Path, Path, CopyOption...)}
     */
    void move(Path source, Path target, CopyOption... options) throws IOException;

    /**
     * A wrapper around {@link Files#exists(Path, LinkOption...)}
     */
    boolean exists(Path path, LinkOption... options);

    /**
     * A wrapper around {@link Files#newBufferedWriter(Path, Charset, OpenOption...)}
     */
    BufferedWriter newBufferedWriter(Path path, Charset cs, OpenOption... options) throws IOException;

    /**
     * A wrapper around {@link Files#newBufferedReader(Path, Charset)}
     */
    BufferedReader newBufferedReader(Path path, Charset cs) throws IOException;

    /**
     * A wrapper around {@link Files#walk(Path, FileVisitOption...)}
     */
    Stream<Path> walk(Path start, FileVisitOption... options) throws IOException;

    /**
     * A wrapper around {@link Files#isRegularFile(Path, LinkOption...)}
     */
    boolean isRegularFile(Path path, LinkOption... options);

    /**
     * A wrapper around {@link Files#lines(Path, Charset)}
     */
    Stream<String> lines(Path path, Charset cs) throws IOException;

    /**
     * A wrapper around {@link Files#deleteIfExists(Path)}
     */
    boolean deleteIfExists(Path path) throws IOException;

    /**
     * A wrapper around {@link Files#size(Path)}
     */
    long size(Path path) throws IOException;

    /**
     * A wrapper around {@link Files#list(Path)}
     */
    Stream<Path> list(Path dbDirectory) throws IOException;
}

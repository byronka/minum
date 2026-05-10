package com.renomad.minum.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * An implementation of {@link IFileUtils} which has custom behavior
 */
public class FakeFileUtils implements IFileUtils {

    public BufferedWriter newBufferedWriterResult;
    public int sizeValue = 0;
    public boolean isRegularFileValue = false;
    public boolean sizeShouldThrow;
    public boolean existsValue = false;
    public boolean readStringShouldThrow = false;
    public boolean deleteShouldThrow;
    public Stream<Path> listValue;
    public String readStringValue = "";
    public boolean writeStringShouldThrow = false;

    @Override
    public void writeString(Path path, String content, OpenOption... options) throws IOException {
        if (writeStringShouldThrow) throw new IOException("JUST FOR TESTING");
    }

    @Override
    public Path write(Path path, Iterable<? extends CharSequence> lines, Charset cs, OpenOption... options) throws IOException {
        return null;
    }

    @Override
    public String readString(Path path) throws IOException {
        if (readStringShouldThrow) throw new IOException("JUST FOR TESTING");
        return readStringValue;
    }

    @Override
    public void deleteDirectoryRecursivelyIfExists(Path myPath) throws IOException {

    }

    @Override
    public void makeDirectory(Path directory) throws IOException {

    }

    @Override
    public byte[] readBinaryFile(String path) throws IOException {
        return new byte[0];
    }

    @Override
    public List<String> readAllLines(Path path) throws IOException {
        return List.of();
    }

    @Override
    public String readTextFile(String path) throws IOException {
        return "";
    }

    @Override
    public void checkFileIsWithinDirectory(String path, String directoryPath) throws IOException {

    }

    @Override
    public Path safeResolve(String parentDirectory, String path) throws IOException {
        return null;
    }

    @Override
    public void delete(Path path) throws IOException {
        if (deleteShouldThrow) throw new IOException("JUST FOR TESTING");
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {

    }

    @Override
    public boolean exists(Path path, LinkOption... options) {
        return existsValue;
    }

    @Override
    public BufferedWriter newBufferedWriter(Path path, Charset cs, OpenOption... options) throws IOException {
        return newBufferedWriterResult;
    }

    @Override
    public BufferedReader newBufferedReader(Path path, Charset cs) throws IOException {
        return null;
    }

    @Override
    public Stream<Path> walk(Path start, FileVisitOption... options) throws IOException {
        return Stream.empty();
    }

    @Override
    public boolean isRegularFile(Path path, LinkOption... options) {
        return isRegularFileValue;
    }

    @Override
    public Stream<String> lines(Path path, Charset cs) throws IOException {
        return Stream.empty();
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
        return false;
    }

    @Override
    public long size(Path path) throws IOException {
        if (sizeShouldThrow) {
            throw new IOException("JUST FOR TESTING");
        }
        return sizeValue;
    }

    @Override
    public Stream<Path> list(Path dbDirectory) throws IOException {
        if (listValue != null) return listValue;
        return Stream.empty();
    }
}

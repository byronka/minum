package com.renomad.minum.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

/**
 * An implementation of {@link com.renomad.minum.utils.IFileUtils} where nearly
 * every method throws IOException
 */
public class ThrowingFileUtils implements IFileUtils {

    @Override
    public void writeString(Path path, String content, OpenOption... options) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public Path write(Path path, Iterable<? extends CharSequence> lines, Charset cs, OpenOption... options) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public String readString(Path path) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public void deleteDirectoryRecursivelyIfExists(Path myPath) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public void makeDirectory(Path directory) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public byte[] readBinaryFile(String path) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public List<String> readAllLines(Path path) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public String readTextFile(String path) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public void checkFileIsWithinDirectory(String path, String directoryPath) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public Path safeResolve(String parentDirectory, String path) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public void delete(Path path) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public boolean exists(Path path, LinkOption... options) {
        return false;
    }

    @Override
    public BufferedWriter newBufferedWriter(Path path, Charset cs, OpenOption... options) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public BufferedReader newBufferedReader(Path path, Charset cs) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public Stream<Path> walk(Path start, FileVisitOption... options) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public boolean isRegularFile(Path path, LinkOption... options) {
        return false;
    }

    @Override
    public Stream<String> lines(Path path, Charset cs) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public long size(Path path) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }

    @Override
    public Stream<Path> list(Path dbDirectory) throws IOException {
        throw new IOException("THIS IS JUST THROWN FOR TESTING");
    }
}

package com.renomad.minum.utils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An interface for the operations of reading a file.
 * This primarily exists to make testing easier.
 */
public interface IFileReader {

    /**
     * Reads a file from disk.
     * @throws com.renomad.minum.security.ForbiddenUseException if the requested path includes bad file patterns,
     * mainly ones to escape from the intended directories (like ".." or "/", etc)
     * @throws IllegalArgumentException if the path is blank
     */
    byte[] readFile(String path) throws IOException;

    /**
     * This returns the {@link ReentrantLock} that is used around
     * the {@link LRUCache} to allow access to the cache from
     * other classes.  This is used in the {@link com.renomad.minum.web.WebFramework}
     * class when accessing the cache for static files.
     */
    ReentrantLock getCacheLock();

    /**
     * Returns the LRU (least-recently-used) cache that is
     * filled when using {@link #readFile(String)}
     */
    Map<String, byte[]> getLruCache();
}

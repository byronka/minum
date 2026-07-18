package com.renomad.minum.utils;

import com.renomad.minum.logging.ILogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.renomad.minum.utils.FileUtils.checkForBadFilePatterns;

/**
 * Reads files from disk, optionally storing into a LRU cache.
 */
public final class FileReader implements IFileReader {

    private final Map<String, byte[]> lruCache;
    private final boolean useCacheForStaticFiles;
    private final ILogger logger;
    private final ReentrantLock cacheLock = new ReentrantLock();

    public FileReader(Map<String, byte[]> lruCache, boolean useCacheForStaticFiles, ILogger logger) {
        this.lruCache = lruCache;
        this.useCacheForStaticFiles = useCacheForStaticFiles;
        this.logger = logger;
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        if (useCacheForStaticFiles && lruCache.containsKey(path)) {
            cacheLock.lock();
            try {
                byte[] bytes = lruCache.get(path);
                logger.logTrace(() -> "in FileReader.readFile, just obtained %d bytes from the cache using a path of %s".formatted(bytes.length, path));
                return bytes;
            } finally {
                cacheLock.unlock();
            }
        }
        checkForBadFilePatterns(path);
        return readTheFile(path, logger, useCacheForStaticFiles, lruCache);
    }

    byte[] readTheFile(String path, ILogger logger, boolean useCacheForStaticFiles, Map<String, byte[]> lruCache) throws IOException {
        try (RandomAccessFile reader = new RandomAccessFile(path, "r");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            FileChannel channel = reader.getChannel();
            int bufferSize = 8 * 1024;
            if ((long) bufferSize > channel.size()) {
                bufferSize = Math.toIntExact(channel.size());
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

                if (useCacheForStaticFiles) {
                    logger.logDebug(() -> "Storing " + path + " in the cache");
                    cacheLock.lock();
                    try {
                        byte[] putResult = lruCache.put(path, bytes);
                        logger.logTrace(() -> ("in FileReader.readTheFile, just added %d bytes with a key " +
                                "of %s. %s").formatted(bytes.length, path,
                                putResult == null ? "No previous value for this key existed" :
                                        ("The previous length of data for this key was " + putResult.length + " bytes")));
                    } finally {
                        cacheLock.unlock();
                    }
                }
                return bytes;
            }
        }
    }

    /**
     * Returns the lock used to prevent concurrent modification
     * exceptions when mutating the LRU cache data.
     * <br>
     * This may be useful if you need to access the LRU cache,
     * which, owing to how a least-recently-used cache works,
     * will cause the data to mutate, and which requires to
     * be protected with locks.
     */
    @Override
    public ReentrantLock getCacheLock() {
        return cacheLock;
    }

    @Override
    public Map<String, byte[]> getLruCache() {
        return this.lruCache;
    }
}

package com.renomad.minum.utils;

import com.renomad.minum.logging.ILogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.renomad.minum.utils.FileUtils.badFilePathPatterns;

/**
 * Reads files from disk, optionally storing into a LRU cache.
 */
public final class FileReader implements IFileReader {

    private final Map<String, byte[]> lruCache;
    private final boolean useCacheForStaticFiles;
    private final ILogger logger;

    public FileReader(Map<String, byte[]> lruCache, boolean useCacheForStaticFiles, ILogger logger) {
        this.lruCache = lruCache;
        this.useCacheForStaticFiles = useCacheForStaticFiles;
        this.logger = logger;
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        if (useCacheForStaticFiles && lruCache.containsKey(path)) {
            return lruCache.get(path);
        }

        if (badFilePathPatterns.matcher(path).find()) {
            logger.logDebug(() -> String.format("Bad path requested at readFile: %s", path));
            return new byte[0];
        }

        if (!Files.exists(Path.of(path))) {
            logger.logDebug(() -> String.format("No file found at %s, returning an empty byte array", path));
            return new byte[0];
        }

        return readTheFile(path, logger, useCacheForStaticFiles, lruCache);
    }

    static byte[] readTheFile(String path, ILogger logger, boolean useCacheForStaticFiles, Map<String, byte[]> lruCache) throws IOException {
        try (RandomAccessFile reader = new RandomAccessFile(path, "r");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            FileChannel channel = reader.getChannel();
            int bufferSize = 8 * 1024;
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

                if (useCacheForStaticFiles) {
                    logger.logDebug(() -> "Storing " + path + " in the cache");
                    lruCache.put(path, bytes);
                }
                return bytes;
            }
        }
    }

}

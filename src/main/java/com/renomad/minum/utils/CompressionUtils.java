package com.renomad.minum.utils;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.renomad.minum.utils.Invariants.mustNotBeNull;

public final class CompressionUtils {

    private CompressionUtils() {}

    /**
     * Compress the given bytes using the GZIP algorithm.
     */
    public static byte[] gzipCompress(byte[] bytes) {
        mustNotBeNull(bytes);
        final var baos = new MyByteArrayOutputStream(new ByteArrayOutputStream());
        return compress(bytes, baos);
    }

    static byte[] compress(byte[] bytes, MyAbstractByteArrayOutputStream baos) {
        try (baos) {
            try (final var gzipOutputStream = new GZIPOutputStream(baos)) {
                gzipOutputStream.write(bytes);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UtilsException(e);
        }
    }

    /**
     * Decompress the given bytes using the GZIP algorithm.
     */
    public static byte[] gzipDecompress(byte[] compressedBytes) {
        mustNotBeNull(compressedBytes);
        final MyByteArrayOutputStream os = new MyByteArrayOutputStream(new ByteArrayOutputStream());
        final ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
        return decompress(os, bis);
    }

    static byte[] decompress(MyAbstractByteArrayOutputStream os, InputStream bis) {
        try (os; bis) {
            final GZIPInputStream gzipInputStream = new GZIPInputStream(bis);
            gzipInputStream.transferTo(os);
            return os.toByteArray();
        } catch (IOException e) {
            throw new UtilsException(e);
        }
    }
}

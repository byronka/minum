package com.renomad.minum.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressionUtils {

    /**
     * Compress the given bytes using the GZIP algorithm.
     */
    public static byte[] gzipCompress(byte[] bytes) {
        try (final var baos = new ByteArrayOutputStream()) {
            try (final var gzipOutputStream = new GZIPOutputStream(baos)) {
                gzipOutputStream.write(bytes);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Decompress the given bytes using the GZIP algorithm.
     */
    public static byte[] gzipDecompress(byte[] compressedBytes) {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream();
             final ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
             final GZIPInputStream gzipInputStream = new GZIPInputStream(bis)) {
            gzipInputStream.transferTo(os);
            return os.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

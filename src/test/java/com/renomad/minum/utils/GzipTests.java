package com.renomad.minum.utils;

import com.renomad.minum.testing.TestFramework;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.renomad.minum.testing.TestFramework.assertEqualByteArray;
import static com.renomad.minum.testing.TestFramework.assertEquals;

public class GzipTests {

    @Test
    public void testGzipCompression() throws IOException {

        // compress
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var gos = new GZIPOutputStream(out);
        String s = Files.readString(Path.of("src/test/resources/gettysburg_address.txt"));
        byte[] uncompressedBytes = s.getBytes(StandardCharsets.UTF_8);
        assertEquals(uncompressedBytes.length, 1510);
        gos.write(uncompressedBytes);

        // finish the compression
        gos.finish();
        byte[] compressedBytes = out.toByteArray();
        assertEquals(compressedBytes.length, 765);

        // decompress
        var gis = new GZIPInputStream(new ByteArrayInputStream(compressedBytes));
        ByteArrayOutputStream uncompressFinalResult = new ByteArrayOutputStream();
        gis.transferTo(uncompressFinalResult);

        // assert
        assertEqualByteArray(uncompressedBytes, uncompressFinalResult.toByteArray());
    }
}

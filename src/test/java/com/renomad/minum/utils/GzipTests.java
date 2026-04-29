package com.renomad.minum.utils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.testing.TestFramework.shutdownTestingContext;

public class GzipTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("GzipTests");
        logger = (TestLogger)context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

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

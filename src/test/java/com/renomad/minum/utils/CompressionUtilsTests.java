package com.renomad.minum.utils;

import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.StopwatchUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.CompressionUtils.gzipCompress;
import static com.renomad.minum.utils.CompressionUtils.gzipDecompress;

/**
 * These tests run the compression utilities through their paces
 */
public class CompressionUtilsTests {


    private static TestLogger logger;
    private static String gettysburgAddress;
    private static Context context;


    @BeforeClass
    public static void init() throws IOException {
        context = buildTestingContext("compression_utils_tests");
        logger = (TestLogger) context.getLogger();
        gettysburgAddress = Files.readString(Path.of("src/test/resources/gettysburg_address.txt"));
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    /**
     * The simplest-possible test to get a sense of how the GzipOutputStream works
     */
    @Test
    public void gzipIntroductionTest() {
        var bytes = new byte[]{1,2,3};
        var expectedBytes = new byte[]{31, -117, 8, 0, 0, 0, 0, 0, 0, -1, 99, 100, 98, 6, 0, 29, -128, -68, 85, 3, 0, 0, 0};
        var compressedBytes = gzipCompress(bytes);
        assertEqualByteArray(compressedBytes, expectedBytes);
    }

    /**
     * Here's a simple test to try compressing a text file
     */
    @Test
    public void gzipTest_textFile() {
        var bytes = gettysburgAddress.getBytes(StandardCharsets.UTF_8);
        var compressedBytes = gzipCompress(bytes);
        // if you want to write this to file, to check things, uncomment below.
        //  try (var fos = new FileOutputStream("foo.zip")) {
        //      fos.write(compressedBytes);
        //  }
        byte[] decompressedBytes = gzipDecompress(compressedBytes);
        String decompressedString = new String(decompressedBytes, StandardCharsets.UTF_8);
        assertEquals(decompressedString, gettysburgAddress);
    }

    /**
     * Let's see what kind of timing and memory usage we get when running
     * this in serial
     */
    @Test
    public void gzipTest_textFile_Performance() {
        StopwatchUtils stopwatchUtils = new StopwatchUtils();
        StopwatchUtils stopwatchUtils1 = stopwatchUtils.startTimer();
        for (int i = 0; i < 100; i++) {
            var bytes = gettysburgAddress.getBytes(StandardCharsets.UTF_8);
            var compressedBytes = gzipCompress(bytes);
            byte[] decompressedBytes = gzipDecompress(compressedBytes);
            String decompressedString = new String(decompressedBytes, StandardCharsets.UTF_8);
            assertEquals(decompressedString, gettysburgAddress);
        }
        long millis = stopwatchUtils1.stopTimer();
        logger.logDebug(() -> "Compression performance test took " + millis + " milliseconds");
    }

    /**
     * How does the system handle if we ask it to compress many
     * requests in parallel?
     */
    @Test
    public void gzipTest_textFile_Performance_highly_parallel() {
        StopwatchUtils stopwatchUtils = new StopwatchUtils();
        StopwatchUtils stopwatchUtils1 = stopwatchUtils.startTimer();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, 100).forEach(i -> {
                var unused = executor.submit(() -> {
                    var bytes = gettysburgAddress.getBytes(StandardCharsets.UTF_8);
                    var compressedBytes = gzipCompress(bytes);
                    byte[] decompressedBytes = gzipDecompress(compressedBytes);
                    String decompressedString = new String(decompressedBytes, StandardCharsets.UTF_8);
                    assertEquals(decompressedString, gettysburgAddress);
                });
            });
        }

        long millis = stopwatchUtils1.stopTimer();
        logger.logDebug(() -> "Compression performance test took " + millis + " milliseconds");
    }

    @Test
    public void test_compress_NullInput() {
        assertThrows(InvariantException.class,
                "value must not be null",
                () -> CompressionUtils.gzipCompress(null));
    }

    @Test
    public void test_decompress_NullInput() {
        assertThrows(InvariantException.class,
                "value must not be null",
                () -> CompressionUtils.gzipDecompress(null));
    }

    @Test
    public void test_compress_Exception() {
        UtilsException ex = assertThrows(UtilsException.class, () -> CompressionUtils.compress(null, new MyAbstractByteArrayOutputStream() {
            @Override
            byte[] toByteArray() {
                return new byte[0];
            }

            @Override
            public void write(int b) throws IOException {
                throw new IOException("Testing IOException in CompressionUtils");
            }
        }));

        assertEquals(ex.getMessage(), "java.io.IOException: Testing IOException in CompressionUtils");
    }

    @Test
    public void test_decompress_Exception() {
        UtilsException ex = assertThrows(UtilsException.class, () -> CompressionUtils.decompress(new MyAbstractByteArrayOutputStream() {
            @Override
            byte[] toByteArray() {
                return new byte[0];
            }

            @Override
            public void write(int b) throws IOException {
                throw new IOException("Testing IOException in CompressionUtils");
            }
        }, new ByteArrayInputStream(new byte[]{31, -117, 8, 0, 0, 0, 0, 0, 0, -1, 99, 100, 98, 6, 0, 29, -128, -68, 85, 3, 0, 0, 0})));

        assertEquals(ex.getMessage(), "java.io.IOException: Testing IOException in CompressionUtils");
    }


}

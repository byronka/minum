package com.renomad.minum.utils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.StopwatchUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
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

    @BeforeClass
    public static void init() {
        var context = buildTestingContext("compression_utils_tests");
        logger = (TestLogger) context.getLogger();
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
                executor.submit(() -> {
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

    String gettysburgAddress = """
                Four score and seven years ago our fathers brought forth on this continent, a 
                new nation, conceived in Liberty, and dedicated to the proposition that all men are created equal.
                                
                Now we are engaged in a great civil war, testing whether that nation, or any nation so 
                conceived and so dedicated, can long endure. We are met on a great battle-field of that 
                war. We have come to dedicate a portion of that field, as a final resting place for 
                those who here gave their lives that that nation might live. It is altogether fitting 
                and proper that we should do this.
                                
                But, in a larger sense, we can not dedicate -- we can not consecrate -- we can not 
                hallow -- this ground. The brave men, living and dead, who struggled here, have 
                consecrated it, far above our poor power to add or detract. The world will little 
                note, nor long remember what we say here, but it can never forget what they did here. 
                It is for us the living, rather, to be dedicated here to the unfinished work which 
                they who fought here have thus far so nobly advanced. It is rather for us to be here 
                dedicated to the great task remaining before us -- that from these honored dead we 
                take increased devotion to that cause for which they gave the last full measure of 
                devotion -- that we here highly resolve that these dead shall not have died in 
                vain -- that this nation, under God, shall have a new birth of freedom -- and that 
                government of the people, by the people, for the people, shall not perish from the earth.
                                
                Abraham Lincoln
                November 19, 1863
                """;

}

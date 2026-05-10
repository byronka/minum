package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * Exposes a bug in BodyProcessor.determineBoundaryValue():
 * when the content-type has parameters after the boundary value
 * (e.g. "boundary=myboundary; charset=utf-8"), the boundary is
 * extracted as "myboundary; charset=utf-8" instead of just "myboundary".
 * This causes multipart parsing to fail because the boundary markers
 * in the body ("--myboundary") don't match the extracted value.
 */
public class BoundaryBugTest {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("BoundaryBugTests");
        logger = (TestLogger) context.getLogger();
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

    /**
     * A multipart request where the Content-Type header has an extra
     * parameter after the boundary value ("; charset=utf-8").
     * The body uses the correct boundary "i_am_a_boundary", but
     * determineBoundaryValue() extracts "i_am_a_boundary; charset=utf-8"
     * which won't match the markers in the body.
     *
     * Expected: parsing succeeds and returns the partition data.
     * Bug, now fixed, was: boundary mismatch causes empty/failed parse.
     */
    @Test
    public void test_BoundaryWithTrailingParams_ShouldStillParse() {
        String body = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text1"\r
                \r
                I am a value that is text\r
                --i_am_a_boundary--\r
                """.stripLeading();
        var bodyProcessor = new BodyProcessor(context);

        // Content-Type has "; charset=utf-8" AFTER the boundary value.
        // determineBoundaryValue() will extract "i_am_a_boundary; charset=utf-8"
        // instead of "i_am_a_boundary", causing a mismatch with the actual
        // boundary markers in the body.
        Body result = bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "multipart/form-data; boundary=i_am_a_boundary; charset=utf-8",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));

        // If the boundary was correctly extracted, we'd find the partition.
        // Instead, the mismatch causes parsing to fail — either returning
        // no partitions or an UNRECOGNIZED body type.
        assertEquals(result.getBodyType(), BodyType.MULTIPART);
        assertFalse(result.getPartitionByName("text1").isEmpty(),
                "Should find partition 'text1' but boundary mismatch causes parse failure");


        // alternate test - if the character after the boundary value is a space.
        Body result2 = bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "multipart/form-data; boundary=i_am_a_boundary ; charset=utf-8",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));

        // If the boundary was correctly extracted, we'd find the partition.
        // Instead, the mismatch causes parsing to fail — either returning
        // no partitions or an UNRECOGNIZED body type.
        assertEquals(result2.getBodyType(), BodyType.MULTIPART);
        assertFalse(result2.getPartitionByName("text1").isEmpty(),
                "Should find partition 'text1' but boundary mismatch causes parse failure");
    }
}

package com.renomad.minum.web;

import com.renomad.minum.Context;
import com.renomad.minum.exceptions.ForbiddenUseException;
import com.renomad.minum.logging.TestLogger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.renomad.minum.testing.TestFramework.*;

public class BodyProcessorTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger) context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    /**
     * Edge case - if a multipart form body is missing a valid name value in its headers
     */
    @Test
    public void test_MultiPart_EdgeCase_MissingNameInHeaders() {
        String body = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; NO_NAME="text1"\r
                \r
                I am a value that is text\r
                --i_am_a_boundary\r
                """.stripIndent();
        var bodyProcessor = new BodyProcessor(context);

        var bodyResult = bodyProcessor.extractBodyFromBytes(
                body.length(),
                "content-type: multipart/form-data; boundary=i_am_a_boundary",
                body.getBytes(StandardCharsets.UTF_8)
                );

        String unableToParseThisBody = logger.findFirstMessageThatContains("No name value found");
        assertTrue(unableToParseThisBody.contains("No name value found in the headers of a partition. Data: --i_am_a_boundary\nContent-Type: text"));
        assertEquals(bodyResult.getKeys(), Set.of());
    }

    /**
     * If the data has a content type of url-encoded form, and we
     * hit malformed data, return useful information in the exception.
     */
    @Test
    public void test_UrlEncoded_EdgeCase_Malformed() {
        String body = """
                Foo
                """.strip();
        var bodyProcessor = new BodyProcessor(context);

        Body bodyResult = bodyProcessor.extractBodyFromBytes(
                body.length(),
                "content-type: application/x-www-form-urlencoded",
                body.getBytes(StandardCharsets.UTF_8)
        );

        String unableToParseThisBody = logger.findFirstMessageThatContains("Unable to parse this body");
        assertEquals(unableToParseThisBody, "Unable to parse this body. returning an empty map and the raw bytes for the body.  Exception message: Range [0, -1) out of bounds for length 3");
        assertEquals(bodyResult.getKeys(), Set.of());
        assertEquals(bodyResult.asString(), "Foo");

    }

    /**
     * If the data has a content type of url-encoded form, and we
     * hit malformed data, return useful information in the exception.
     */
    @Test
    public void test_UrlEncoded_EdgeCase_MalformedLargeData() {
        String body = "a".repeat(BodyProcessor.MAX_SIZE_DATA_RETURNED_IN_EXCEPTION + 1);
        var bodyProcessor = new BodyProcessor(context);

        var bodyResult = bodyProcessor.extractBodyFromBytes(
                body.length(),
                "content-type: application/x-www-form-urlencoded",
                body.getBytes(StandardCharsets.UTF_8)
        );

        String unableToParseThisBody = logger.findFirstMessageThatContains("Unable to parse this body");
        assertEquals(unableToParseThisBody, "Unable to parse this body. returning an empty map and the raw bytes for the body.  Exception message: Range [0, -1) out of bounds for length 1025");
        assertEquals(bodyResult.getKeys(), Set.of());
        assertTrue(bodyResult.asString().contains("aaaaaaaaaa ... (remainder of data trimmed)"));
    }


    /**
     * Edge case - if a multipart form body is missing a valid name value in its headers, with large data
     */
    @Test
    public void test_MultiPart_EdgeCase_MissingNameInHeaders_LargeData() {
        String body =
                """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; NO_NAME="text1"\r
                \r
                I am a value that is
                """ +
                        "a".repeat(BodyProcessor.MAX_SIZE_DATA_RETURNED_IN_EXCEPTION+1) +
                """
                text\r
                --i_am_a_boundary\r
                """.stripIndent();
        var bodyProcessor = new BodyProcessor(context);

        var bodyResult = bodyProcessor.extractBodyFromBytes(
                body.length(),
                "content-type: multipart/form-data; boundary=i_am_a_boundary",
                body.getBytes(StandardCharsets.UTF_8)
        );

        String unableToParseThisBody = logger.findFirstMessageThatContains("No name value found in the headers", 1);
        assertTrue(unableToParseThisBody.contains("No name value found in the headers of a partition"));
        assertTrue(unableToParseThisBody.contains("Data: --i_am_a_boundary\r\nContent-Type:"));
        assertTrue(unableToParseThisBody.contains("text/plain\r\nContent-Disposition: form-data; NO_NAME=\"text1\"\r\n\r\nI am a value that"));
        assertTrue(unableToParseThisBody.contains("aaaaaaaaaa ... (remainder of data trimmed)"));
        assertEquals(bodyResult.getKeys(), Set.of());
    }

    @Test
    public void test_extractBodyFromBytes_EdgeCase_NoValidBoundaryFound() {
        var bodyProcessor = new BodyProcessor(context);

        // the content type should be multipart form data and also mention the boundary value -
        // we are not including it, leading to this edge case branch being invoked.
        Body result = bodyProcessor.extractBodyFromBytes(25, "multipart/form-data", new byte[0]);

        assertTrue(logger.doesMessageExist("Did not find a valid boundary value for the multipart input"));
        assertEquals(result, new Body(Map.of(), new byte[0], Map.of()));
    }

    @Test
    public void test_extractBodyFromBytes_EdgeCase_contentLengthZero() {
        var bodyProcessor = new BodyProcessor(context);
        Body result = bodyProcessor.extractBodyFromBytes(0, "application/x-www-form-urlencoded", new byte[0]);
        assertEqualByteArray(result.asBytes(), new byte[0]);
        assertTrue(logger.doesMessageExist("did not recognize a key-value pattern content-type, returning an empty map and the raw bytes for the body"));
    }

    @Test
    public void test_tokenizer_HappyPath() {
        List<String> results = BodyProcessor.tokenizer("a,b,c", ',', 10);
        assertEqualsDisregardOrder(results, List.of("a","b","c"));
    }

    @Test
    public void test_tokenizer_PartitionCountExceeded() {
        var ex = assertThrows(ForbiddenUseException.class, () -> BodyProcessor.tokenizer("a,b,c", ',', 2));
        assertEquals(ex.getMessage(), "Request made for too many partitions in the tokenizer.  Current max: 2");
    }

    @Test
    public void test_extractData() {
        var bodyProcessor = new BodyProcessor(context);
        var inputStream = new ByteArrayInputStream("2\r\nab\r\n0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        Headers headers = new Headers(List.of("Content-Length: 0"), context);

        Body body = bodyProcessor.extractData(inputStream, headers);
        assertEquals(body.asString(), "ab");
    }

}

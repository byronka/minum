package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.StopwatchUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_200_OK;

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
     * Edge case - if a multipart form body is missing a valid name value in its headers, ah
     * well, such is life.  WOn't be particularly useful - but then, if we're getting
     * such malformed data, probably warrants further investigation into what exactly
     * is going on.
     */
    @Test
    public void test_MultiPart_EdgeCase_MissingNameInHeaders() {
        String body = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; NO_NAME="text1"\r
                \r
                I am a value that is text\r
                --i_am_a_boundary--\r
                """.stripLeading();
        var bodyProcessor = new BodyProcessor(context);

        bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "content-type: multipart/form-data; boundary=i_am_a_boundary",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));
        assertTrue(logger.doesMessageExist("Unable to parse this body. returning what we have so far.  Exception message: Error: No name value set on multipart partition"));
    }

    /**
     * It is absolutely possible to have basically nothing returned in one of the
     * partitions - all that needs to happen is for the user to provide no data for
     * an input.
     */
    @Test
    public void test_MultiPart_EdgeCase_NoContentInPartition() {
        String multipartBody = """
                --i_am_a_boundary\r
                Content-Disposition: form-data; name="text1"\r
                \r
                \r
                --i_am_a_boundary--\r
                """.stripLeading();
        var bodyProcessor = new BodyProcessor(context);

        Body body = bodyProcessor.extractBodyFromInputStream(
                multipartBody.length(),
                "content-type: multipart/form-data; boundary=i_am_a_boundary",
                new ByteArrayInputStream(multipartBody.getBytes(StandardCharsets.US_ASCII)));

        var text = body.getPartitionByName("text1").getFirst();
        assertEquals("", text.getContentAsString());

    }

    /**
     * If the data has a content type of url-encoded form, and we
     * hit malformed data, return useful information in the exception.
     */
    @Test
    public void test_UrlEncoded_EdgeCase_Malformed() {
        String body = "Foo";
        var bodyProcessor = new BodyProcessor(context);

        Body bodyResult = bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "content-type: application/x-www-form-urlencoded",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));

        assertTrue(logger.doesMessageExist("Unable to parse this body. no key found during parsing"));
        assertEquals(bodyResult.getBodyType(), BodyType.UNRECOGNIZED);
    }

    /**
     * Verify that BodyProcessor correctly identifies and rejects requests
     * with a Content-Length that exceeds our memory safety limits (MAX_READ_SIZE_BYTES).
     */
    @Test
    public void test_BodyProcessor_LargePayload_Rejection() {
        var bodyProcessor = new BodyProcessor(context);
        var constants = context.getConstants();

        // Construct headers with a content length just at the limit
        Headers headers = new Headers(List.of(
                "Content-Type: text/plain",
                "Content-Length: " + constants.maxReadSizeBytes
        ));

        // It should throw a ForbiddenUseException
        assertThrows(com.renomad.minum.security.ForbiddenUseException.class,
                () -> bodyProcessor.extractData(new ByteArrayInputStream(new byte[0]), headers));
    }

    /**
     * If the data has a content type of url-encoded form, and we
     * hit malformed data, return useful information in the exception.
     */
    @Test
    public void test_UrlEncoded_EdgeCase_MalformedLargeData() {
        String body = "a".repeat(BodyProcessor.MAX_SIZE_DATA_RETURNED_IN_EXCEPTION + 1);
        var bodyProcessor = new BodyProcessor(context);

        var bodyResult = bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "content-type: application/x-www-form-urlencoded",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));

        String unableToParseThisBody = logger.findFirstMessageThatContains("Unable to parse this body", 1);
        assertEquals(unableToParseThisBody, "Unable to parse this body. returning what we have so far.  Exception message: Maximum size for name attribute is 50 ascii characters");
        assertEquals(bodyResult.getKeys(), Set.of());
        assertEquals(bodyResult.toString(), "Body{bodyMap={}, bodyType=UNRECOGNIZED}");
    }

    /**
     * I found a bug while writing a program - when requesting the data
     * from a request multiple times, the second time it would fail because
     * the stream was exhausted.  The solution was to cache the body
     * in the request.
     */
    @Test
    public void test_Request_CachingBody() {
        String body = "a=1&b=2";
        var bodyProcessor = new BodyProcessor(context);

        Body bodyResult1 = bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "content-type: application/x-www-form-urlencoded",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));

        assertEquals("1", bodyResult1.asString("a"));
        assertEquals("2", bodyResult1.asString("b"));
    }

    @Test
    public void test_getMultiPartIterable_IOException() {
        var bodyProcessor = new BodyProcessor(context);
        InputStream is = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Testing");
            }
        };
        var iterable = bodyProcessor.getMultiPartIterable(is, "boundary", 100);
        var iterator = iterable.iterator();
        // Trigger the read by calling next()
        assertThrows(WebServerException.class, iterator::next);
    }

    @Test
    public void test_MultiPart_LargeData_Performance() {
        int size = 10 * 1024 * 1024; // 10MB
        byte[] largeData = new byte[size];
        new java.util.Random().nextBytes(largeData);
        String boundary = "boundary";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
            baos.write("Content-Disposition: form-data; name=\"file\"; filename=\"large.bin\"\r\n".getBytes(StandardCharsets.US_ASCII));
            baos.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            baos.write(largeData);
            baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        byte[] bodyBytes = baos.toByteArray();
        var bodyProcessor = new BodyProcessor(context);

        StopwatchUtils sw = new StopwatchUtils().startTimer();
        Body body = bodyProcessor.extractBodyFromInputStream(
                bodyBytes.length,
                "content-type: multipart/form-data; boundary=" + boundary,
                new ByteArrayInputStream(bodyBytes));
        long time = sw.stopTimer();
        System.out.println("Time to parse 10MB multipart: " + time + "ms");

        var partitions = body.getPartitionByName("file");
        assertEquals(1, partitions.size());
        assertEqualByteArray(largeData, partitions.getFirst().getContent());
    }

    @Test
    public void test_UrlEncoded_MultipleValues() {
        // In Minum, for URL-encoded forms, if duplicate keys are sent, the last one wins.
        String body = "a=1&a=2&b=3";
        var bodyProcessor = new BodyProcessor(context);

        Body bodyResult = bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "content-type: application/x-www-form-urlencoded",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));

        assertEquals("2", bodyResult.asString("a"));
        assertEquals("3", bodyResult.asString("b"));
    }

    @Test
    public void test_extractData_Chunked() {
        var bodyProcessor = new BodyProcessor(context);
        Headers headers = new Headers(List.of("Transfer-Encoding: chunked"));
        Body body = bodyProcessor.extractData(new ByteArrayInputStream(new byte[0]), headers);
        assertEquals(Body.EMPTY, body);
        assertTrue(logger.doesMessageExist("client sent chunked transfer-encoding.  Minum does not automatically read bodies of this type."));
    }

    @Test
    public void test_parseMultipartForm_BlankBoundary() {
        var bodyProcessor = new BodyProcessor(context);
        // This is a private method, but we can trigger it via extractBodyFromInputStream
        // by providing a blank boundary in the content-type.
        Body body = bodyProcessor.extractBodyFromInputStream(10, "multipart/form-data; boundary= ", new ByteArrayInputStream(new byte[0]));
        assertTrue(logger.doesMessageExist("The boundary value was blank for the multipart input. Returning an empty map"));
        assertEquals(Body.EMPTY.asString(), body.asString());
    }

    @Test
    public void test_MultiPart_EdgeCase_NoBoundaryInContentType() {
        var bodyProcessor = new BodyProcessor(context);
        bodyProcessor.extractBodyFromInputStream(10, "multipart/form-data", new ByteArrayInputStream(new byte[0]));
        assertTrue(logger.doesMessageExist("The boundary value was blank for the multipart input. Returning an empty map"));
    }

}

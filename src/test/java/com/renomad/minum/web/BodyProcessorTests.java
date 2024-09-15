package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.InvariantException;
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
     * from multipart, it was including an extra carriage-return plus line-feed
     * at the end.  This test is to examine the issue more closely.
     */
    @Test
    public void test_MultiPart_Avoid_ExtraBytes() {
        String body = """
                ------WebKitFormBoundaryEdMgstSu0ppszI8o\r
                Content-Disposition: form-data; name="myfile"; filename="one.txt"\r
                Content-Type: text/plain\r
                \r
                1\r
                ------WebKitFormBoundaryEdMgstSu0ppszI8o--\r
                """;
        var bodyProcessor = new BodyProcessor(context);

        Body bodyResult = bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryEdMgstSu0ppszI8o",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));
        assertEqualByteArray(bodyResult.getPartitionByName("myfile").getFirst().getContent(), "1".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * I found a bug while writing a program - when requesting the data
     * from multipart, it was including an extra carriage-return plus line-feed
     * at the end.  This test is to examine the issue more closely.
     */
    @Test
    public void test_MultiPart_Avoid_ExtraBytes_MultiplePartitions() {
        String body = """
                ------WebKitFormBoundaryEdMgstSu0ppszI8o\r
                Content-Disposition: form-data; name="myfile"; filename="one.txt"\r
                Content-Type: text/plain\r
                \r
                1\r
                ------WebKitFormBoundaryEdMgstSu0ppszI8o\r
                Content-Disposition: form-data; name="myfile2"; filename="one.txt"\r
                Content-Type: text/plain\r
                \r
                2\r
                ------WebKitFormBoundaryEdMgstSu0ppszI8o--\r
                """;
        var bodyProcessor = new BodyProcessor(context);

        Body bodyResult = bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryEdMgstSu0ppszI8o",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));
        assertEqualByteArray(bodyResult.getPartitionByName("myfile").getFirst().getContent(), "1".getBytes(StandardCharsets.UTF_8));
        assertEqualByteArray(bodyResult.getPartitionByName("myfile2").getFirst().getContent(), "2".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * I found a bug while writing a program - when requesting the data
     * from multipart, it was including an extra carriage-return plus line-feed
     * at the end.  This test is to examine the issue more closely.
     */
    @Test
    public void test_MultiPart_Avoid_ExtraBytes_MultiplePartitions_Bytes() {
        String body = """
                ------WebKitFormBoundaryEdMgstSu0ppszI8o\r
                Content-Disposition: form-data; name="myfile"; filename="one.txt"\r
                Content-Type: application/octet-stream\r
                \r
                """+new String(new byte[]{1,2,3}, StandardCharsets.UTF_8)+"""
                \r
                ------WebKitFormBoundaryEdMgstSu0ppszI8o\r
                Content-Disposition: form-data; name="myfile2"; filename="two.txt"\r
                Content-Type: application/octet-stream\r
                \r
                """+new String(new byte[]{4,5,6}, StandardCharsets.UTF_8)+"""
                \r
                ------WebKitFormBoundaryEdMgstSu0ppszI8o--\r
                """;
        var bodyProcessor = new BodyProcessor(context);

        Body bodyResult = bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryEdMgstSu0ppszI8o",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));
        var ex = assertThrows(WebServerException.class, () -> bodyResult.asBytes("myfile"));
        assertEquals(ex.getMessage(), "Request body is in multipart format.  Use .getPartitionByName instead");
        assertEqualByteArray(bodyResult.getPartitionByName("myfile").getFirst().getContent(), new byte[]{1,2,3});
        assertEqualByteArray(bodyResult.getPartitionByName("myfile2").getFirst().getContent(), new byte[]{4,5,6});
    }

    /**
     * If the input element has the "multiple" attribute, the multipart
     * message will have the name multiple times, but with differing
     * filenames.  How should we handle that?
     */
    @Test
    public void test_MultiPart_MultipleFilesSameInputName() {
        String body = """
                ------WebKitFormBoundaryEdMgstSu0ppszI8o\r
                Content-Disposition: form-data; name="myfile"; filename="one.txt"\r
                Content-Type: application/octet-stream\r
                \r
                """+new String(new byte[]{1,2,3}, StandardCharsets.UTF_8)+"""
                \r
                ------WebKitFormBoundaryEdMgstSu0ppszI8o\r
                Content-Disposition: form-data; name="myfile"; filename="two.txt"\r
                Content-Type: application/octet-stream\r
                \r
                """+new String(new byte[]{4,5,6}, StandardCharsets.UTF_8)+"""
                \r
                ------WebKitFormBoundaryEdMgstSu0ppszI8o--\r
                """;
        var bodyProcessor = new BodyProcessor(context);

        Body bodyResult = bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryEdMgstSu0ppszI8o",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));
        var ex = assertThrows(WebServerException.class, () -> bodyResult.asBytes("myfile"));
        assertEquals(ex.getMessage(), "Request body is in multipart format.  Use .getPartitionByName instead");
        List<Partition> filePartitions = bodyResult.getPartitionByName("myfile");
        assertEqualByteArray(filePartitions.getFirst().getContent(), new byte[]{1,2,3});
        assertEqualByteArray(filePartitions.getLast().getContent(), new byte[]{4,5,6});
    }

    @Test
    public void test_extractBodyFromBytes_EdgeCase_NoValidBoundaryFound() {
        var bodyProcessor = new BodyProcessor(context);

        // the content type should be multipart form data and also mention the boundary value -
        // we are not including it, leading to this edge case branch being invoked.
        Body result = bodyProcessor.extractBodyFromInputStream(25, "multipart/form-data", new ByteArrayInputStream(new byte[0]));

        assertTrue(logger.doesMessageExist("The boundary value was blank for the multipart input. Returning an empty map"));
        assertEquals(result, new Body(Map.of(), new byte[0], List.of(), BodyType.UNRECOGNIZED));
    }

    @Test
    public void test_extractBodyFromBytes_EdgeCase_contentLengthZero() {
        var bodyProcessor = new BodyProcessor(context);
        Body result = bodyProcessor.extractBodyFromInputStream(0, "application/x-www-form-urlencoded", new ByteArrayInputStream(new byte[0]));
        assertEqualByteArray(result.asBytes(), new byte[0]);
        assertEquals(result.getBodyType(), BodyType.NONE);
        assertTrue(logger.doesMessageExist("the length of the body was 0, returning an empty Body"));
    }

    /**
     * Sending a photo of a cute kitty
     */
    @Test
    public void test_ExtractBodyFromBytes_Image() throws IOException {
        byte[] kittyImageBytes = Files.readAllBytes(Path.of("src/test/resources/kitty.jpg"));
        final var baos = new ByteArrayOutputStream();
        baos.write(
                """
                 ------WebKitFormBoundaryGlzbZJMmR2xSuAaT\r
                 Content-Disposition: form-data; name="person_id"\r
                 \r
                 1a3e2d86-639f-49f8-8278-4e769a4d7222\r
                 ------WebKitFormBoundaryGlzbZJMmR2xSuAaT\r
                 Content-Disposition: form-data; name="image_uploads"; filename="kitty.jpg"\r
                 Content-Type: image/png\r
                 \r
                 """.getBytes(StandardCharsets.UTF_8));
        baos.write(kittyImageBytes);
        baos.write(
                """
                \r
                ------WebKitFormBoundaryGlzbZJMmR2xSuAaT--\r
                
                """.getBytes(StandardCharsets.UTF_8));
        var bodyProcessor = new BodyProcessor(context);
        byte[] byteArray = baos.toByteArray();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);

        StopwatchUtils stopwatch = new StopwatchUtils().startTimer();
        Body bodyResult = bodyProcessor.extractBodyFromInputStream(
                byteArray.length,
                "Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryGlzbZJMmR2xSuAaT",
                inputStream);

        byte[] bytes = bodyResult.getPartitionByName("image_uploads").getFirst().getContent();
        assertEquals(kittyImageBytes.length, bytes.length);
        assertEquals(bodyResult.getBodyType(), BodyType.MULTIPART);
        assertEqualByteArray(kittyImageBytes, bytes);
        long timeTakenMillis = stopwatch.stopTimer();
        logger.logDebug(() -> "Took " + timeTakenMillis + " milliseconds to process this multipart data");
    }




    /**
     * Current design decision is to log that we
     * don't handle this and return an empty body.
     */
    @Test
    public void test_ChunkedTransfer_NegativeCase() {
        var bodyProcessor = new BodyProcessor(context);
        var inputStream = new ByteArrayInputStream("2\r\nab\r\n0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        Headers headers = new Headers(List.of("Transfer-Encoding: chunked"));

        Body body = bodyProcessor.extractData(inputStream, headers);
        assertEquals(body.asString(), "");
        assertTrue(logger.doesMessageExist("client sent chunked transfer-encoding.  Minum does not automatically read bodies of this type."));
    }

    /**
     * If we receive a request with a content type but no data, we'll return
     * an empty body instance.
     */
    @Test
    public void test_extractData_Empty() {
        var bodyProcessor = new BodyProcessor(context);
        var inputStream = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        Headers headers = new Headers(List.of());

        Body body = bodyProcessor.extractData(inputStream, headers);
        assertEquals(body, Body.EMPTY);
    }

    @Test
    public void test_GettingCorrectContentType_MissingContentType() {
        var response = (Response)Response.buildResponse(CODE_200_OK, Map.of(), "foo foo");
        var ex = assertThrows(InvariantException.class, () -> WebFramework.confirmBodyHasContentType(null, response));
        assertEquals(ex.getMessage(), "a Content-Type header must be specified in the Response object if it returns data. Response details: Response{statusCode=CODE_200_OK, extraHeaders={}, bodyLength=7} Request: null");
    }

    /**
     * If we receive data in url format, it will be in
     * key-value format, like a = hello and b = 123.
     */
    @Test
    public void test_DataByKey_HappyPath() {
        String body = "a=hello&b=123";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII));
        var bodyProcessor = new BodyProcessor(context);

        Body bodyResult = bodyProcessor.extractBodyFromInputStream(
                body.length(),
                "content-type: application/x-www-form-urlencoded",
                inputStream);

        assertEquals(bodyResult.getKeys(), Set.of("a","b"));
        assertEquals(bodyResult.asString("a"), "hello");
        assertEquals(bodyResult.asString("b"), "123");
        assertEquals(bodyResult.getBodyType(), BodyType.FORM_URL_ENCODED);
    }

    /**
     * If we request a body to be processed many times, it should still work fine.
     * This is written to assert correct behavior, related to a bug that was
     * added to the codebase in version 7.0.0
     * <br>
     * In that case, the count of partitions was a class property and was not getting
     * reset between calls, eventually leading to the system failing to read request
     * bodies.  This test passing proves that the issue no longer exists.
     */
    @Test
    public void test_EdgeCase() {
        var bodyProcessor = new BodyProcessor(context);

        for (int i = 0; i < IBodyProcessor.MAX_BODY_KEYS_URL_ENCODED + 2; i++) {
            String body = "a=hello&b=123";
            ByteArrayInputStream inputStream = new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII));
            Body bodyResult = bodyProcessor.extractBodyFromInputStream(
                    body.length(),
                    "content-type: application/x-www-form-urlencoded",
                    inputStream);

            assertEquals(bodyResult.getKeys(), Set.of("a","b"));
            assertEquals(bodyResult.asString("a"), "hello");
            assertEquals(bodyResult.asString("b"), "123");
            assertEquals(bodyResult.getBodyType(), BodyType.FORM_URL_ENCODED);
        }
    }


}

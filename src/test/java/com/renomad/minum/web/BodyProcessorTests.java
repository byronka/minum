package com.renomad.minum.web;

import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.InvariantException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
     * Edge case - if a multipart form body is missing a valid name value in its headers
     */
    @Test
    public void test_MultiPart_EdgeCase_MissingNameInHeaders() {
        String body = """
                \r
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; NO_NAME="text1"\r
                \r
                I am a value that is text\r
                --i_am_a_boundary--\r
                """.stripIndent();
        var bodyProcessor = new BodyProcessor(context);

        var bodyResult = bodyProcessor.extractBodyFromBytes(
                body.length(),
                "content-type: multipart/form-data; boundary=i_am_a_boundary",
                body.getBytes(StandardCharsets.UTF_8)
                );

        assertEquals(bodyResult, new Body(Map.of(), body.getBytes(StandardCharsets.UTF_8), Map.of()));
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
                \r
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; NO_NAME="text1"\r
                \r
                I am a value that is
                """ +
                        "a".repeat(BodyProcessor.MAX_SIZE_DATA_RETURNED_IN_EXCEPTION+1) +
                """
                text\r
                --i_am_a_boundary--\r
                """.stripIndent();
        var bodyProcessor = new BodyProcessor(context);

        var bodyResult = bodyProcessor.extractBodyFromBytes(
                body.length(),
                "content-type: multipart/form-data; boundary=i_am_a_boundary",
                body.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(bodyResult, new Body(Map.of(), body.getBytes(StandardCharsets.UTF_8), Map.of()));
    }

    /**
     * I found a bug while writing a program - when requesting the data
     * from multipart, it was including an extra carriage-return plus line-feed
     * at the end.  This test is to examine the issue more closely.
     */
    @Test
    public void test_MultiPart_Avoid_ExtraBytes() {
        String body = """
                \r
                ------WebKitFormBoundaryEdMgstSu0ppszI8o\r
                Content-Disposition: form-data; name="myfile"; filename="one.txt"\r
                Content-Type: text/plain\r
                \r
                1\r
                ------WebKitFormBoundaryEdMgstSu0ppszI8o--\r
                """;
        var bodyProcessor = new BodyProcessor(context);

        Body bodyResult = bodyProcessor.extractBodyFromBytes(
                body.length(),
                "Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryEdMgstSu0ppszI8o",
                body.getBytes(StandardCharsets.UTF_8)
        );
        assertEqualByteArray(bodyResult.asBytes("myfile"), "1".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * I found a bug while writing a program - when requesting the data
     * from multipart, it was including an extra carriage-return plus line-feed
     * at the end.  This test is to examine the issue more closely.
     */
    @Test
    public void test_MultiPart_Avoid_ExtraBytes_MultiplePartitions() {
        String body = """
                \r
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

        Body bodyResult = bodyProcessor.extractBodyFromBytes(
                body.length(),
                "Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryEdMgstSu0ppszI8o",
                body.getBytes(StandardCharsets.UTF_8)
        );
        assertEqualByteArray(bodyResult.asBytes("myfile"), "1".getBytes(StandardCharsets.UTF_8));
        assertEqualByteArray(bodyResult.asBytes("myfile2"), "2".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * I found a bug while writing a program - when requesting the data
     * from multipart, it was including an extra carriage-return plus line-feed
     * at the end.  This test is to examine the issue more closely.
     */
    @Test
    public void test_MultiPart_Avoid_ExtraBytes_MultiplePartitions_Bytes() {
        String body = """
                \r
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

        Body bodyResult = bodyProcessor.extractBodyFromBytes(
                body.length(),
                "Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryEdMgstSu0ppszI8o",
                body.getBytes(StandardCharsets.UTF_8)
        );
        assertEqualByteArray(bodyResult.asBytes("myfile"), new byte[]{1,2,3});
        assertEqualByteArray(bodyResult.asBytes("myfile2"), new byte[]{4,5,6});
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

    /**
     * Sending a photo of a cute kitty
     */
    @Test
    public void test_ExtractBodyFromBytes_Image() throws IOException {
        byte[] kittyImageBytes = Files.readAllBytes(Path.of("src/test/resources/kitty.jpg"));
        final var baos = new ByteArrayOutputStream();
        baos.write(
                """
                 \r
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

        Body bodyResult = bodyProcessor.extractBodyFromBytes(
                byteArray.length,
                "Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryGlzbZJMmR2xSuAaT",
                byteArray
        );

        byte[] bytes = bodyResult.asBytes("image_uploads");
        assertEquals(kittyImageBytes.length, bytes.length);
        assertEqualByteArray(kittyImageBytes, bytes);
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
        Headers headers = new Headers(List.of("Content-Length: 0"));

        Body body = bodyProcessor.extractData(inputStream, headers);
        assertEquals(body.asString(), "ab");
    }

    @Test
    public void test_GettingCorrectContentType_MissingContentType() {
        var response = Response.buildResponse(CODE_200_OK, Map.of(), "foo foo");
        var ex = assertThrows(InvariantException.class, () -> WebFramework.confirmBodyHasContentType(null, response, new StringBuilder()));
        assertEquals(ex.getMessage(), "a Content-Type header must be specified in the Response object if it returns data. Response details: Response{statusCode=CODE_200_OK, extraHeaders={}, body=[102, 111, 111, 32, 102, 111, 111], bodyLength=7} Request: null");
    }

    @Test
    public void test_PotentiallyCompress_HappyPath() {
        StringBuilder headerStringBuilder = new StringBuilder();
        Headers headers = new Headers(List.of("accept-encoding: gzip"));
        Map<String, String> extraHeaders = Map.of("content-type", "text/plain");
        VaryHeader varyHeader = new VaryHeader();

        Response response = Response.buildResponse(CODE_200_OK, extraHeaders, "foo bar".repeat(1000));
        var response2 = WebFramework.potentiallyCompress(
                headers,
                response,
                headerStringBuilder,
                varyHeader);

        assertEquals(response2.getBodyLength(), (long) 55);
    }

    /**
     * If the content type does not include "text", we won't compress.
     */
    @Test
    public void test_PotentiallyCompress_MissingContentType() {
        StringBuilder headerStringBuilder = new StringBuilder();
        Headers headers = new Headers(List.of("accept-encoding: gzip"));
        Map<String, String> extraHeaders = Map.of("content-type", "");
        VaryHeader varyHeader = new VaryHeader();

        Response response = Response.buildResponse(CODE_200_OK, extraHeaders, "foo bar".repeat(1000));
        WebFramework.potentiallyCompress(
                headers,
                response,
                headerStringBuilder,
                varyHeader);

        assertEquals(response.getBodyLength(),  (long) 7000);
    }

    /**
     * If we receive data in url format, it will be in
     * key-value format, like a = hello and b = 123.
     */
    @Test
    public void test_DataByKey_HappyPath() {
        String body = "a=hello&b=123";
        var bodyProcessor = new BodyProcessor(context);

        Body bodyResult = bodyProcessor.extractBodyFromBytes(
                body.length(),
                "content-type: application/x-www-form-urlencoded",
                body.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(bodyResult.getKeys(), Set.of("a","b"));
        assertEquals(bodyResult.asString("a"), "hello");
        assertEquals(bodyResult.asString("b"), "123");

    }
}

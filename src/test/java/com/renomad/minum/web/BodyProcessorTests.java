package com.renomad.minum.web;

import com.renomad.minum.Context;
import com.renomad.minum.htmlparsing.ParsingException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static com.renomad.minum.testing.TestFramework.*;

public class BodyProcessorTests {

    private static Context context;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
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

        var exception = assertThrows(ParsingException.class, () -> bodyProcessor.extractBodyFromBytes(
                body.length(),
                "content-type: multipart/form-data; boundary=i_am_a_boundary",
                body.getBytes(StandardCharsets.UTF_8)
                ));

        assertEquals(exception.getMessage(), "Unable to parse this body");
        assertEquals(exception.getCause().getMessage(), "No name value found in the headers of a partition. Data: --i_am_a_boundary\nContent-Type: text/plain\nContent-Disposition: form-data; NO_NAME=\"text1\"\n\nI am a value that is text\n--i_am_a_boundary\n");
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

        ParsingException parsingException = assertThrows(ParsingException.class, () -> bodyProcessor.extractBodyFromBytes(
                body.length(),
                "content-type: application/x-www-form-urlencoded",
                body.getBytes(StandardCharsets.UTF_8)
        ));

        assertEquals(parsingException.getMessage(), "Unable to parse this body");
        assertEquals(parsingException.getCause().getMessage(), "Unable to parse this body as application/x-www-form-urlencoded. Data: Foo");
    }

    /**
     * If the data has a content type of url-encoded form, and we
     * hit malformed data, return useful information in the exception.
     */
    @Test
    public void test_UrlEncoded_EdgeCase_MalformedLargeData() {
        String body = "a".repeat(BodyProcessor.MAX_SIZE_DATA_RETURNED_IN_EXCEPTION + 1);
        var bodyProcessor = new BodyProcessor(context);

        ParsingException parsingException = assertThrows(ParsingException.class, () -> bodyProcessor.extractBodyFromBytes(
                body.length(),
                "content-type: application/x-www-form-urlencoded",
                body.getBytes(StandardCharsets.UTF_8)
        ));

        assertEquals(parsingException.getMessage(), "Unable to parse this body");
        assertTrue(parsingException.getCause().getMessage().contains("aaaaaaaaaa ... (remainder of data trimmed)"));
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

        var exception = assertThrows(ParsingException.class, () -> bodyProcessor.extractBodyFromBytes(
                body.length(),
                "content-type: multipart/form-data; boundary=i_am_a_boundary",
                body.getBytes(StandardCharsets.UTF_8)
        ));

        assertEquals(exception.getMessage(), "Unable to parse this body");
        assertTrue(exception.getCause().getMessage().contains("aaaaaaaaaaaaaa ... (remainder of data trimmed)"));
    }
}

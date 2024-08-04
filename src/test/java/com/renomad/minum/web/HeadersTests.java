package com.renomad.minum.web;

import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.logging.ThrowingSupplier;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.InvariantException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;

public class HeadersTests {

    private static Context context;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("header tests");
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    @Test
    public void test_GetAllHeaders_EdgeCase_TooMany() {
        String input = "foo: bar\r\n".repeat(72);
        InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

        var ex = assertThrows(ForbiddenUseException.class, () -> Headers.getAllHeaders(inputStream, mockInputStreamUtils(() -> "foo: bar")));
        assertEquals(ex.getMessage(), "User tried sending too many headers.  max: 70");
    }

    @Test
    public void test_GetAllHeaders_EdgeCase_ValueIsNull() {
        String input = """
                foo: bar
                biz: baz
                """;
        InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

        var result = Headers.getAllHeaders(inputStream, mockInputStreamUtils(() -> null));
        assertEquals(result, new ArrayList<>());
    }

    @Test
    public void test_GetAllHeaders_EdgeCase_IOException() {
        String input = """
                foo: bar
                biz: baz
                """;
        InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        IInputStreamUtils throwingInputStreamUtils = mockInputStreamUtils(() -> {
            throw new IOException("just a test");
        });

        var ex = assertThrows(WebServerException.class, () -> Headers.getAllHeaders(inputStream, throwingInputStreamUtils));
        assertEquals(ex.getMessage(), "java.io.IOException: just a test");
    }

    private IInputStreamUtils mockInputStreamUtils(ThrowingSupplier<String, IOException> readLineAction) {

        return new IInputStreamUtils() {
            @Override public String readLine(InputStream inputStream) throws IOException { return readLineAction.get(); }
            @Override public byte[] read(int lengthToRead, InputStream inputStream) {return new byte[0];}
        };
    }

    /**
     * In this case, the header has no colon - that's malformed.
     * In that case, we simply skip it.
     */
    @Test
    public void test_extractHeadersToMap_EdgeCase_Malformed() {
        Map<String, List<String>> result = Headers.extractHeadersToMap(List.of("foo bar"));
        assertEquals(result.size(), 0);
    }

    /**
     * We have a helper method to get the content type
     */
    @Test
    public void test_ContentType_HappyPath() {
        Headers headers = new Headers(List.of("content-type: foo"));
        String contentType = headers.contentType();
        assertEquals(contentType, "content-type: foo");
    }

    /**
     * If they send more than one content type, it's invalid.
     */
    @Test
    public void test_ContentType_TooMany() {
        Headers headers = new Headers(List.of("content-type: foo", "content-type: bar"));
        var ex = assertThrows(WebServerException.class, headers::contentType);
        assertEquals(ex.getMessage(), "The number of content-type headers must be exactly zero or one.  Received: [content-type: foo, content-type: bar]");
    }

    /**
     * If they send more than one content length, it's invalid.
     */
    @Test
    public void test_ContentLength_TooMany() {
        Headers headers = new Headers(List.of("content-length: 12", "content-length: 44"));
        var ex = assertThrows(WebServerException.class, headers::contentLength);
        assertEquals(ex.getMessage(), "The number of content-length headers must be exactly zero or one.  Received: [content-length: 12, content-length: 44]");
    }

    @Test
    public void test_ContentLength_Negative() {
        Headers headers = new Headers(List.of("content-length: -123"));
        var ex = assertThrows(InvariantException.class, headers::contentLength);
        assertEquals(ex.getMessage(), "Content-length cannot be negative");
    }

    @Test
    public void test_HasKeepAlive() {
        Headers headers = new Headers(List.of("connection: keep-alive"));
        assertTrue(headers.hasKeepAlive(), "should have keep-alive on");
        Headers headers2 = new Headers(List.of(""));
        assertFalse(headers2.hasKeepAlive(), "should not have keep-alive on");
    }

    @Test
    public void test_HasConnectionClose() {
        Headers headers = new Headers(List.of("connection: close"));
        assertTrue(headers.hasConnectionClose());
    }

}

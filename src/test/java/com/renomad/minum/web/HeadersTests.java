package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.logging.ThrowingSupplier;
import com.renomad.minum.state.Context;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;

public class HeadersTests {

    private Context context;
    private TestLogger logger;

    @Before
    public void init() {
        this.context = buildTestingContext("header tests");
        this.logger = (TestLogger) context.getLogger();
    }

    @After
    public void cleanup() {
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
        var ex = assertThrows(BadRequestException.class, () -> Headers.extractHeadersToMap(List.of("foo bar")));
        assertEquals(ex.getMessage(), "Invalid formatting on header in request, was expecting to find a colon separating key from value: foo bar");
    }

    /**
     * We have a helper method to get the content type
     */
    @Test
    public void test_ContentType_HappyPath() {
        Headers headers = new Headers(List.of("content-type: foo"));
        String contentType = headers.contentType();
        assertEquals(contentType, "foo");
    }

    /**
     * If they send more than one content type, it's invalid.
     */
    @Test
    public void test_ContentType_TooMany() {
        Headers headers = new Headers(List.of("content-type: foo", "content-type: bar"));
        var ex = assertThrows(BadRequestException.class, () -> headers.contentType());
        assertEquals(ex.getMessage(), "The number of content-type headers must be exactly zero or one.  Received: [bar, foo]");
    }

    /**
     * If they send more than one content length, it's invalid.
     */
    @Test
    public void test_ContentLength_TooMany() {
        Headers headers = new Headers(List.of("content-length: 44", "content-length: 12"));
        var ex = assertThrows(BadRequestException.class, () -> headers.contentLength());
        assertEquals(ex.getMessage(), "Received multiple content-length headers, which does not make sense.  Received: [12, 44]");
    }

    @Test
    public void test_ContentLength_Negative() {
        var headers = new Headers(List.of("content-length: -123"));
        var ex = assertThrows(BadRequestException.class, () ->  headers.contentLength());
        assertEquals(ex.getMessage(), "Content length cannot be negative.  Received: -123");
    }

    /**
     * If the content length is non-numeric, an exception gets thrown
     */
    @Test
    public void test_ContentLength_NonNumeric() {
        var headers = new Headers(List.of("content-length: abc"));
        var ex = assertThrows(BadRequestException.class, () ->  headers.contentLength());
        assertEquals(ex.getMessage(), "Received a non-numeric content length value. Received: abc");
    }

    @Test
    public void test_HasKeepAlive() {
        Headers headers = new Headers(List.of("connection: keep-alive"));
        assertTrue(headers.hasKeepAlive(), "should have keep-alive on");
        Headers headers2 = new Headers(List.of("foo: bar"));
        assertFalse(headers2.hasKeepAlive(), "should not have keep-alive on");
    }

    @Test
    public void test_HasConnectionClose() {
        Headers headers = new Headers(List.of("connection: close"));
        assertTrue(headers.hasConnectionClose());
    }

    @Test
    public void test_IsEmpty() {
        Headers headers = Headers.EMPTY;
        assertTrue(headers.isEmpty());

        Headers headers2 = new Headers(List.of());
        assertTrue(headers2.isEmpty());
    }

}

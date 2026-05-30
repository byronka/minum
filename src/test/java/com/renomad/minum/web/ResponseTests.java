package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.IFileUtils;
import com.renomad.minum.utils.ThrowingFileUtils;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.testing.TestFramework.shutdownTestingContext;
import static com.renomad.minum.web.Response.buildLargeFileResponse;
import static com.renomad.minum.web.Response.buildStreamingResponse;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_200_OK;

public class ResponseTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("ResponseTests");
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
     * If we use two different {@link Response} as keys in a
     * map, they will be treated as the same if they have
     * the same content.  That is why here, I can use
     * response1 as a key to get response2's value.
     */
    @Test
    public void testUseResponseAsKey() {
        var myMap = new HashMap<IResponse, Integer>();
        IResponse response1 = Response.htmlOk("foo");
        IResponse response2 = Response.htmlOk("foo");

        myMap.put(response1, 42);
        myMap.put(response2, 88);

        assertEquals(myMap.get(response1), 88);
    }

    @Test
    public void testToString() {
        IResponse response1 = Response.htmlOk("fooabcdefg");
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders=Headers{headerStrings=[Content-Type: text/html; charset=UTF-8]}, bodyLength=10, isBodyText=true}");
        response1 = Response.htmlOk("fooabcdefgh");
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders=Headers{headerStrings=[Content-Type: text/html; charset=UTF-8]}, bodyLength=11, isBodyText=true}");
        response1 = Response.htmlOk("fooabcdefghi");
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders=Headers{headerStrings=[Content-Type: text/html; charset=UTF-8]}, bodyLength=12, isBodyText=true}");
    }

    /**
     * If an Exception is thrown while sending the body, it should
     * be converted to an IOException.  This is what we expect to happen
     * when we use our custom {@link ThrowingConsumer}
     */
    @Test
    public void testResponse_EdgeCase_SendBodyWithException() {
        Response response = (Response)Response.buildResponse(CODE_200_OK, Map.of(), "hello");
        // This is just used to force an IOException to be thrown when running sendBody
        ISocketWrapper mockSocketWrapper = new ISocketWrapper() {
            @Override public void send(String msg) {}
            @Override public void send(byte[] bodyContents) {throw new WebServerException("This is just a test");}
            @Override public void send(byte[] bodyContents, int off, int len) {}
            @Override public void send(int b) {}
            @Override public void sendHttpLine(String msg) {}
            @Override public int getLocalPort() {return 0;}
            @Override public SocketAddress getRemoteAddrWithPort() {return null;}
            @Override public String getRemoteAddr() {return null;}
            @Override public HttpServerType getServerType() {return null;}
            @Override public void close() {}
            @Override public InputStream getInputStream() {return null;}
            @Override public String getHostName() {return null;}
            @Override public void flush() {}
        };
        var ex = assertThrows(WebServerException.class, () ->  response.sendBody(mockSocketWrapper));
        assertEquals(ex.getMessage(), "This is just a test");
    }

    /**
     * If a file path is supplied to the buildLargeFileResponse method with characters
     * that could be used to escape the directory, an exception will be thrown.
     */
    @Test
    public void testResponse_EdgeCase_BadPathRequested() {
        var fileUtils = new FileUtils(this.context.getLogger(), this.context.getConstants());
        assertThrows(ForbiddenUseException.class, "filename (../foo) contained invalid characters", () -> buildLargeFileResponse(Map.of(), "../foo", ".", new Headers(List.of()), fileUtils));
        assertThrows(ForbiddenUseException.class, "filename (c:/foo) contained invalid characters (:).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> buildLargeFileResponse(Map.of(), "c:/foo", ".", new Headers(List.of()), fileUtils));
        assertThrows(ForbiddenUseException.class, "filename (//foo) contained invalid characters", () -> buildLargeFileResponse(Map.of(), "//foo", ".", new Headers(List.of()), fileUtils));
        buildLargeFileResponse(Map.of(), "src/test/resources/kitty.jpg", ".", new Headers(List.of()), fileUtils);
    }

    @Test
    public void testResponse_Streaming() throws Exception {
        FakeSocketWrapper fakeSocketWrapper = new FakeSocketWrapper();
        Response response = (Response)buildStreamingResponse(CODE_200_OK, Map.of(), sw -> sw.send("hello"));
        response.sendBody(fakeSocketWrapper);
        String s = ((ByteArrayOutputStream) fakeSocketWrapper.os).toString(StandardCharsets.UTF_8);
        assertEquals(s, "hello");
    }

    /**
     * Something to watch out for is users providing data with carriage-return plus
     * line-feed, meaning they can cause new headers to be added to the response.
     * To avoid that, we'll set a whitelist of allowable characters for the location.
     */
    @Test
    public void testRedirect() {
        // should not throw anything
        Response.redirectTo("foo");

        // these should throw exceptions
        var result = assertThrows(WebServerException.class, () -> Response.redirectTo("\r\n"));
        assertEquals(result.getMessage(), "Failure in redirect to (\r\n). Exception: java.lang.IllegalArgumentException: Illegal character in path at index 0: \r\n");
        var result2 = assertThrows(WebServerException.class, () -> Response.redirectTo(null));
        assertEquals(result2.getMessage(), "Failure in redirect to (null). Exception: java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"this.input\" is null");
    }

    /**
     * The parameter in the constructor for extra headers is not allowed to be null,
     * though it may be set to {@link Headers#EMPTY}.
     */
    @Test
    public void testNullExtraHeaders() {
        var ex1 = assertThrows(IllegalArgumentException.class, () -> new Response(CODE_200_OK, null, new byte[0], (x) -> {}, 0L, false));
        assertEquals(ex1.getMessage(), "Extra headers must not be null (may use Headers.EMPTY)");
    }

    /**
     * The parameter in the constructor for status code is not allowed to be null
     */
    @Test
    public void testNullStatusCode() {
        var ex1 = assertThrows(IllegalArgumentException.class, () -> new Response(null, Headers.EMPTY, new byte[0], (x) -> {}, 0L, false));
        assertEquals(ex1.getMessage(), "Status code must not be null");
    }

    /**
     * a test to go through some variations of the {@link Response#buildLargeFileResponse(Headers, String, String, Headers, IFileUtils)}
     * method, to get a handle on its behavior.
     */
    @Test
    public void testBuildLargeFileResponse() {
        var fileUtils = new FileUtils(this.context.getLogger(), this.context.getConstants());
        String filePath = "src/test/resources/kitty.jpg";

        {
            Map<String,String> extraHeaders = Map.of();
            Headers requestHeaders = new Headers(List.of());
            IResponse response = buildLargeFileResponse(extraHeaders, filePath, requestHeaders, fileUtils);
            assertEquals(response.getStatusCode(), CODE_200_OK);
        }
        {
            Headers requestHeaders = new Headers(List.of());
            Headers extraHeaders = new Headers(List.of());
            IResponse response = buildLargeFileResponse(extraHeaders, filePath, requestHeaders, fileUtils);
            assertEquals(response.getStatusCode(), CODE_200_OK);
        }
        {
            Headers extraHeaders = new Headers(List.of());
            Headers requestHeaders = new Headers(List.of());
            String parentDirectory = "./";
            IResponse response = buildLargeFileResponse(extraHeaders, filePath, parentDirectory, requestHeaders, fileUtils);
            assertEquals(response.getStatusCode(), CODE_200_OK);
        }
        {
            Map<String,String> extraHeaders = Map.of();
            Headers requestHeaders = new Headers(List.of());
            String parentDirectory = "./";
            IResponse response = buildLargeFileResponse(extraHeaders, filePath, parentDirectory, requestHeaders, fileUtils);
            assertEquals(response.getStatusCode(), CODE_200_OK);
        }

    }

    /**
     * In this test, the {@link Response#buildLargeFileResponse(Headers, String, Headers, IFileUtils)}
     * handles an {@link IOException} thrown when we look for a file's size.
     */
    @Test
    public void test_buildLargeFileResponse_NegativeCase_ExceptionThrown() {
        var fileUtils = new ThrowingFileUtils();
        String filePath = "src/test/resources/kitty.jpg";
        Headers requestHeaders = new Headers(List.of());
        Headers extraHeaders = new Headers(List.of());

        var ex = assertThrows(WebServerException.class, () -> buildLargeFileResponse(extraHeaders, filePath, requestHeaders, fileUtils));

        assertEquals(ex.getMessage(), "Error in Response.buildLargeFileResponse");
        assertEquals(ex.getCause().getMessage(), "THIS IS JUST THROWN FOR TESTING");
    }

    /**
     * In this test, the {@link Response#buildLargeFileResponse(Headers, String, String, Headers, IFileUtils)}
     * handles an {@link IOException} thrown when we look for a file's size.
     */
    @Test
    public void test_buildLargeFileResponse_NegativeCase_ExceptionThrown_Overload1() {
        var fileUtils = new ThrowingFileUtils();
        String filePath = "src/test/resources/kitty.jpg";
        Headers extraHeaders = new Headers(List.of());
        Headers requestHeaders = new Headers(List.of());
        String parentDirectory = "./";

        var ex = assertThrows(WebServerException.class, () -> buildLargeFileResponse(extraHeaders, filePath, parentDirectory, requestHeaders, fileUtils));

        assertEquals(ex.getMessage(), "Error at Response.buildLargeFileResponse");
        assertEquals(ex.getCause().getMessage(), "THIS IS JUST THROWN FOR TESTING");
    }

    /**
     * If the {@link java.net.Socket} throws an exception in this method,
     * it gets wrapped.
     */
    @Test
    public void test_sendByteArrayResponse_NegativeCase_ExceptionThrown() {
        var sw = new ISocketWrapper() {
            @Override public void send(String msg) throws IOException {}
            @Override public void send(byte[] bodyContents) throws IOException {
                throw new IOException("JUST A TEST");
            }
            @Override public void send(byte[] bodyContents, int off, int len) throws IOException {}
            @Override public void send(int b) throws IOException {}
            @Override public void sendHttpLine(String msg) throws IOException {}
            @Override public int getLocalPort() {return 0;}
            @Override public SocketAddress getRemoteAddrWithPort() {return null;}
            @Override public String getRemoteAddr() {return "";}
            @Override public HttpServerType getServerType() {return null;}
            @Override public InputStream getInputStream() {return null;}
            @Override public String getHostName() {return "";}
            @Override public void flush() throws IOException {}
            @Override public void close() throws IOException {}
        };

        var ex = assertThrows(IOException.class, () -> sw.send(new byte[0]));

        assertEquals(ex.getMessage(), "JUST A TEST");
    }

}

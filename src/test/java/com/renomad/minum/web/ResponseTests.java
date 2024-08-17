package com.renomad.minum.web;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertThrows;
import static com.renomad.minum.web.Response.buildStreamingResponse;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_200_OK;

public class ResponseTests {

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
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders={Content-Type=text/html; charset=UTF-8}, bodyLength=10}");
        response1 = Response.htmlOk("fooabcdefgh");
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders={Content-Type=text/html; charset=UTF-8}, bodyLength=11}");
        response1 = Response.htmlOk("fooabcdefghi");
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders={Content-Type=text/html; charset=UTF-8}, bodyLength=12}");
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
            @Override public void send(byte[] bodyContents) throws IOException {throw new IOException("This is just a test");}
            @Override public void send(byte[] bodyContents, int off, int len) throws IOException {}
            @Override public void send(int b) throws IOException {}
            @Override public void sendHttpLine(String msg) {}
            @Override public int getLocalPort() {return 0;}
            @Override public SocketAddress getRemoteAddrWithPort() {return null;}
            @Override public String getRemoteAddr() {return null;}
            @Override public HttpServerType getServerType() {return null;}
            @Override public void close() {}
            @Override public InputStream getInputStream() {return null;}
            @Override public String getHostName() {return null;}
        };
        var ex = assertThrows(IOException.class, () ->  response.sendBody(mockSocketWrapper));
        assertEquals(ex.getMessage(), "This is just a test");
    }

    /**
     * If a file path is supplied to the buildLargeFileResponse method with characters
     * that could be used to escape the directory, an exception will be thrown.
     */
    @Test
    public void testResponse_EdgeCase_BadPathRequested() {
        assertThrows(WebServerException.class, () -> Response.buildLargeFileResponse(Map.of(), "../foo", new Headers(List.of())));
        assertThrows(WebServerException.class, () -> Response.buildLargeFileResponse(Map.of(), "c:/foo", new Headers(List.of())));
        assertThrows(WebServerException.class, () -> Response.buildLargeFileResponse(Map.of(), "//foo", new Headers(List.of())));
    }

    @Test
    public void testResponse_Streaming() throws IOException {
        FakeSocketWrapper fakeSocketWrapper = new FakeSocketWrapper();
        Response response = (Response)buildStreamingResponse(CODE_200_OK, Map.of(), sw -> sw.send("hello"));
        response.sendBody(fakeSocketWrapper);
        String s = ((ByteArrayOutputStream) fakeSocketWrapper.os).toString(StandardCharsets.UTF_8);
        assertEquals(s, "hello");
    }

}

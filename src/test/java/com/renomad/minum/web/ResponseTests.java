package com.renomad.minum.web;

import com.renomad.minum.utils.InvariantException;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertThrows;
import static com.renomad.minum.web.Response.buildResponse;
import static com.renomad.minum.web.Response.buildStreamingResponse;

public class ResponseTests {

    /**
     * If we use two different {@link Response} as keys in a
     * map, they will be treated as the same if they have
     * the same content.  That is why here, I can use
     * response1 as a key to get response2's value.
     */
    @Test
    public void testUseResponseAsKey() {
        var myMap = new HashMap<Response, Integer>();
        Response response1 = Response.htmlOk("foo");
        Response response2 = Response.htmlOk("foo");

        myMap.put(response1, 42);
        myMap.put(response2, 88);

        assertEquals(myMap.get(response1), 88);
    }

    @Test
    public void testToString() {
        Response response1 = Response.htmlOk("fooabcdefg");
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders={Content-Type=text/html; charset=UTF-8}, body=[102, 111, 111, 97, 98, 99, 100, 101, 102, 103], bodyLength=10}");
        response1 = Response.htmlOk("fooabcdefgh");
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders={Content-Type=text/html; charset=UTF-8}, body=[102, 111, 111, 97, 98, 99, 100, 101, 102, 103, 104], bodyLength=11}");
        response1 = Response.htmlOk("fooabcdefghi");
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders={Content-Type=text/html; charset=UTF-8}, body=[102, 111, 111, 97, 98, 99, 100, 101, 102, 103, 104, 105], bodyLength=12}");
    }

    /**
     * If an Exception is thrown while sending the body, it should
     * be converted to an IOException.  This is what we expect to happen
     * when we use our custom {@link ThrowingConsumer}
     */
    @Test
    public void testResponse_EdgeCase_SendBodyWithException() {
        Response response = Response.buildLeanResponse(StatusLine.StatusCode.CODE_200_OK);
        // This is just used to force an IOException to be thrown when running sendBody
        ISocketWrapper mockSocketWrapper = new ISocketWrapper() {
            @Override public void send(String msg) throws IOException {}
            @Override public void send(byte[] bodyContents) throws IOException {throw new IOException("This is just a test");}
            @Override public void sendHttpLine(String msg) throws IOException {}
            @Override public int getLocalPort() {return 0;}
            @Override public SocketAddress getRemoteAddrWithPort() {return null;}
            @Override public String getRemoteAddr() {return null;}
            @Override public HttpServerType getServerType() {return null;}
            @Override public void close() throws IOException {}
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
        assertThrows(WebServerException.class, () -> Response.buildLargeFileResponse(StatusLine.StatusCode.CODE_200_OK, Map.of(), "../foo"));
        assertThrows(WebServerException.class, () -> Response.buildLargeFileResponse(StatusLine.StatusCode.CODE_200_OK, Map.of(), "c:/foo"));
        assertThrows(WebServerException.class, () -> Response.buildLargeFileResponse(StatusLine.StatusCode.CODE_200_OK, Map.of(), "//foo"));
    }
}

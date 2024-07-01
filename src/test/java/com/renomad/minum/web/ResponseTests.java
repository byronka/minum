package com.renomad.minum.web;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static com.renomad.minum.testing.TestFramework.*;

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
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders={Content-Type=text/html; charset=UTF-8}, body=[102, 111, 111, 97, 98, 99, 100, 101, 102, 103]}");
        response1 = Response.htmlOk("fooabcdefgh");
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders={Content-Type=text/html; charset=UTF-8}, body=[102, 111, 111, 97, 98, 99, 100, 101, 102, 103]...}");
        response1 = Response.htmlOk("fooabcdefghi");
        assertEquals(response1.toString(), "Response{statusCode=CODE_200_OK, extraHeaders={Content-Type=text/html; charset=UTF-8}, body=[102, 111, 111, 97, 98, 99, 100, 101, 102, 103]...}");
    }

    @Test
    public void testResponseConstructor1() {
        Response response = new Response(StatusLine.StatusCode.CODE_200_OK, new byte[]{1, 2, 3});
        assertTrue(response.getExtraHeaders().isEmpty());
        assertEquals(response.getStatusCode(), StatusLine.StatusCode.CODE_200_OK);
        assertEqualByteArray(response.getBody(), new byte[]{1, 2, 3});
    }

    @Test
    public void testResponseConstructor2() {
        Response response = new Response(StatusLine.StatusCode.CODE_200_OK, "testing");
        assertTrue(response.getExtraHeaders().isEmpty());
        assertEquals(response.getStatusCode(), StatusLine.StatusCode.CODE_200_OK);
        assertEquals(new String(response.getBody(), StandardCharsets.UTF_8), "testing");
    }
}

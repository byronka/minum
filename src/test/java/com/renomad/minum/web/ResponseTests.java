package com.renomad.minum.web;

import org.junit.Test;

import java.util.HashMap;

import static com.renomad.minum.testing.TestFramework.*;

public class ResponseTests {

    /**
     * Two {@link Response} should be considered equal
     * if they have equal content.
     */
    @Test
    public void testTwoResponsesEqual() {
        Response response1 = Response.htmlOk("foo");
        Response response2 = Response.htmlOk("foo");
        assertEquals(response1, response2);
    }

    @Test
    public void testTwoResponsesNotEqual() {
        Response response1 = Response.htmlOk("foo");
        Response response2 = Response.htmlOk("bar");
        assertFalse(response1.equals(response2));
    }

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
        Response response1 = Response.htmlOk("foo");
        assertEquals(response1.toString(), "Response{statusCode=_200_OK, extraHeaders={Content-Type=text/html; charset=UTF-8}, body=[102, 111, 111]}");
    }
}

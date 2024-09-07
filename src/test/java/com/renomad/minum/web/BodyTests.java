package com.renomad.minum.web;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertThrows;

public class BodyTests {

    @Test
    public void testGettingValue_EdgeCase_WhenNotFound() {
        Body empty = Body.EMPTY;
        assertEquals("", empty.asString("foo"));
    }

    @Test
    public void testGettingValue_EdgeCase_MissingKey() {
        // a, b, c, easy as 1, 2, 3, do re mi ...
        Map<String, byte[]> bodyMap = Map.of("abc", new byte[]{1, 2, 3});
        Body body = new Body(bodyMap, new byte[0], List.of(), BodyType.FORM_URL_ENCODED);
        assertEquals("", body.asString("foo"));
    }

    /**
     * If the body is of type BodyType.MULTIPART, then asString
     * won't do much useful.  Throw an exception in that case.
     */
    @Test
    public void testAsString_EdgeCase_BodyIsMultipart() {
        Body body = new Body(Map.of(), new byte[0], List.of(), BodyType.MULTIPART);
        var ex = assertThrows(WebServerException.class, () -> body.asString("foo"));
        assertEquals(ex.getMessage(), "Request body is in multipart format.  Use .getPartitionByName instead");
    }

    /**
     * If the body is of type BodyType.UNRECOGNIZED, then asString
     * won't do much useful.  Throw an exception in that case.
     */
    @Test
    public void testAsString_EdgeCase_BodyIsUnrecognized() {
        Body body = new Body(Map.of(), new byte[0], List.of(), BodyType.UNRECOGNIZED);
        var ex = assertThrows(WebServerException.class, () -> body.asString("foo"));
        assertEquals(ex.getMessage(), "Request body is not in a recognized key-value encoding.  Use .asString() to obtain the body data");
    }

    @Test
    public void testAsBytes_EdgeCase_Empty() {
        Body empty = Body.EMPTY;
        assertEquals(0, empty.asBytes("foo").length);
    }

    /**
     * If we ask for bytes and there's no body, just return an empty array
     */
    @Test
    public void testAsBytes_EdgeCase_Empty_2() {
        Body empty = Body.EMPTY;
        assertEquals(0, empty.asBytes().length);
    }

    /**
     * If the body is of type BodyType.MULTIPART, then asBytes
     * won't do much useful.  Throw an exception in that case.
     */
    @Test
    public void testAsBytes_EdgeCase_BodyIsMultipart() {
        Body body = new Body(Map.of(), new byte[0], List.of(), BodyType.MULTIPART);
        var ex = assertThrows(WebServerException.class, () -> body.asBytes("foo"));
        assertEquals(ex.getMessage(), "Request body is in multipart format.  Use .getPartitionByName instead");
    }

    /**
     * If the body is of type BodyType.UNRECOGNIZED, then asBytes
     * won't do much useful.  Throw an exception in that case.
     */
    @Test
    public void testAsBytes_EdgeCase_BodyIsUnrecognized() {
        Body body = new Body(Map.of(), new byte[0], List.of(), BodyType.UNRECOGNIZED);
        var ex = assertThrows(WebServerException.class, () -> body.asBytes("foo"));
        assertEquals(ex.getMessage(), "Request body is not in a recognized key-value encoding.  Use .asBytes() to obtain the body data");
    }

    @Test
    public void testGetPartitionHeaders_EdgeCase_Empty() {
        Body empty = Body.EMPTY;
        assertEquals(0, empty.getPartitionHeaders().size());
    }

    @Test
    public void testGetPartitionHeaders_EdgeCase_BodyIsUnrecognized() {
        Body body = new Body(Map.of(), new byte[0], List.of(), BodyType.UNRECOGNIZED);
        var ex = assertThrows(WebServerException.class, body::getPartitionHeaders);
        assertEquals(ex.getMessage(), "Request body encoded is not encoded in a recognized format. getPartitionHeaders is only used with multipart encoded data.");
    }

    @Test
    public void testGetPartitionHeaders_EdgeCase_BodyIsUrlEncoded() {
        Body body = new Body(Map.of(), new byte[0], List.of(), BodyType.FORM_URL_ENCODED);
        var ex = assertThrows(WebServerException.class, body::getPartitionHeaders);
        assertEquals(ex.getMessage(), "Request body encoded in form-urlencoded format. getPartitionHeaders is only used with multipart encoded data.");
    }

    @Test
    public void testGetPartitionByName_EdgeCase_Empty() {
        Body empty = Body.EMPTY;
        assertEquals(0, empty.getPartitionByName("foo").size());
    }


    @Test
    public void testGetPartitionByName_EdgeCase_UrlEncoded() {
        Body body = new Body(Map.of(), new byte[0], List.of(), BodyType.FORM_URL_ENCODED);
        var ex = assertThrows(WebServerException.class, () -> body.getPartitionByName("foo"));
        assertEquals(ex.getMessage(), "Request body encoded in form-urlencoded format. use .asString(key) or asBytes(key)");
    }

    @Test
    public void testGetPartitionByName_EdgeCase_Unrecognized() {
        Body body = new Body(Map.of(), new byte[0], List.of(), BodyType.UNRECOGNIZED);
        var ex = assertThrows(WebServerException.class, () -> body.getPartitionByName("foo"));
        assertEquals(ex.getMessage(), "Request body encoded is not encoded in a recognized format. use .asString() or asBytes()");
    }

    @Test
    public void testGetKeys_EdgeCase_Empty() {
        Body empty = Body.EMPTY;
        assertEquals(0, empty.getKeys().size());
    }

}

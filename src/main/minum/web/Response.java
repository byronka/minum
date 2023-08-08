package minum.web;

import java.util.*;

/**
 * Represents an HTTP response. This is what will get sent back to the
 * client (that is, to the browser).  There are a variety of overloads
 * of this record for different situations.  The overarching paradigm is
 * to provide you high flexibility.
 * <p>
 * A response message is sent by a server to a client as a reply to its former request message.
 * </p>
 * <h3>
 * Response syntax
 * </h3>
 * <p>
 * A server sends response messages to the client, which consist of:
 * </p>
 * <ul>
 * <li>
 * a status line, consisting of the protocol version, a space, the
 * response status code, another space, a possibly empty reason
 * phrase, a carriage return and a line feed, e.g.:
 * <pre>
 * HTTP/1.1 200 OK
 * </pre>
 * </li>
 *
 * <li>
 * zero or more response header fields, each consisting of the case-insensitive
 * field name, a colon, optional leading whitespace, the field value, an
 * optional trailing whitespace and ending with a carriage return and a line feed, e.g.:
 * <pre>
 * Content-Type: text/html
 * </pre>
 * </li>
 *
 * <li>
 * an empty line, consisting of a carriage return and a line feed;
 * </li>
 *
 * <li>
 * an optional message body.
 * </li>
 *</ul>
 * @param extraHeaders extra headers we want to return with the response.
 */
public record Response(StatusLine.StatusCode statusCode, Map<String, String> extraHeaders,
                       byte[] body) {
    public Response(StatusLine.StatusCode statusCode, byte[] body) {
        this(statusCode, Map.of(), body);
    }

    public Response(StatusLine.StatusCode statusCode, String body) {
        this(statusCode, Map.of(), body.getBytes());
    }

    public Response(StatusLine.StatusCode statusCode, String body, Map<String, String> extraHeaders) {
        this(statusCode, extraHeaders, body.getBytes());
    }

    public Response(StatusLine.StatusCode statusCode, byte[] body, Map<String, String> extraHeaders) {
        this(statusCode, extraHeaders, body);
    }

    public Response(StatusLine.StatusCode statusCode, Map<String, String> extraHeaders) {
        this(statusCode, extraHeaders, "".getBytes());
    }

    public Response(StatusLine.StatusCode statusCode) {
        this(statusCode, Map.of(), "".getBytes());
    }

    /**
     * A helper method to create a response that returns a
     * 303 status code ("see other").  Provide a url that will
     * be handed to the browser.  This url may be relative or absolute.
     */
    public static Response redirectTo(String locationUrl) {
        return new Response(StatusLine.StatusCode._303_SEE_OTHER, Map.of("location", locationUrl));
    }

    /**
     * If you are returning HTML text with a 200 ok, this is a helper that
     * lets you skip some of the boilerplate. This version of the helper
     * lets you add extra headers on top of the basic content-type headers
     * that are needed to specify this is HTML.
     */
    public static Response htmlOk(String body, Map<String, String> extraHeaders) {
        var headers = new HashMap<String, String>();
        headers.put("Content-Type", "text/html; charset=UTF-8");
        headers.putAll(extraHeaders);
        return new Response(StatusLine.StatusCode._200_OK, body, headers);
    }

    /**
     * If you are returning HTML text with a 200 ok, this is a helper that
     * lets you skip some of the boilerplate.
     */
    public static Response htmlOk(String body) {
        return htmlOk(body, Map.of());
    }
}

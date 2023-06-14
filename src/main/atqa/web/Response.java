package atqa.web;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents an HTTP response. This is what will get sent back to the
 * client (that is, to the browser).  There are a variety of overloads
 * of this record for different situations.  The overarching paradigm is
 * to provide you high flexibility.
 * @param extraHeaders extra headers we want to return with the response.
 */
public record Response(StatusLine.StatusCode statusCode, List<String> extraHeaders,
                       byte[] body) {
    public Response(StatusLine.StatusCode statusCode, byte[] body) {
        this(statusCode, Collections.emptyList(), body);
    }

    public Response(StatusLine.StatusCode statusCode, String body) {
        this(statusCode, Collections.emptyList(), body.getBytes());
    }

    public Response(StatusLine.StatusCode statusCode, String body, List<String> extraHeaders) {
        this(statusCode, extraHeaders, body.getBytes());
    }

    public Response(StatusLine.StatusCode statusCode, byte[] body, List<String> extraHeaders) {
        this(statusCode, extraHeaders, body);
    }

    public Response(StatusLine.StatusCode statusCode, List<String> extraHeaders) {
        this(statusCode, extraHeaders, "".getBytes());
    }

    public Response(StatusLine.StatusCode statusCode, List<String> extraHeaders, String body) {
        this(statusCode, extraHeaders, body.getBytes());
    }

    public Response(StatusLine.StatusCode statusCode) {
        this(statusCode, Collections.emptyList(), "".getBytes());
    }

    public static Response redirectTo(String locationUrl) {
        return new Response(StatusLine.StatusCode._303_SEE_OTHER, List.of("location: " + locationUrl));
    }

    /**
     * If you are returning HTML text with a 200 ok, this is a helper that
     * lets you skip some of the boilerplate. This version of the helper
     * lets you add extra headers on top of the basic content-type headers
     * that are needed to specify this is HTML.
     */
    public static Response htmlOk(String body, List<String> extraHeaders) {
        var headers = Arrays.asList("Content-Type: text/html; charset=UTF-8");
        headers.addAll(extraHeaders);
        return new Response(StatusLine.StatusCode._200_OK, body, headers);
    }

    /**
     * If you are returning HTML text with a 200 ok, this is a helper that
     * lets you skip some of the boilerplate.
     */
    public static Response htmlOk(String body) {
        return htmlOk(body, List.of());
    }
}

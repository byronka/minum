package atqa.web;

import java.util.Collections;
import java.util.List;

/**
 * Represents an HTTP response.
 * @param extraHeaders extra headers we want to return with the response.
 */
public record Response(StatusLine.StatusCode statusCode, ContentType contentType, List<String> extraHeaders,
                       byte[] body) {
    public Response(StatusLine.StatusCode statusCode, ContentType contentType, byte[] body) {
        this(statusCode, contentType, Collections.emptyList(), body);
    }

    public Response(StatusLine.StatusCode statusCode, ContentType contentType, String body) {
        this(statusCode, contentType, Collections.emptyList(), body.getBytes());
    }

    public Response(StatusLine.StatusCode statusCode, ContentType contentType, String body,  List<String> extraHeaders) {
        this(statusCode, contentType, extraHeaders, body.getBytes());
    }

    public Response(StatusLine.StatusCode statusCode, ContentType contentType, List<String> extraHeaders) {
        this(statusCode, contentType, extraHeaders, "".getBytes());
    }

    public Response(StatusLine.StatusCode statusCode, List<String> extraHeaders) {
        this(statusCode, ContentType.NONE, extraHeaders, "".getBytes());
    }

    public Response(StatusLine.StatusCode statusCode) {
        this(statusCode, ContentType.NONE, Collections.emptyList(), "".getBytes());
    }
}

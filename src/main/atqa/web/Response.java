package atqa.web;

import java.util.Collections;
import java.util.List;

public record Response(StatusLine.StatusCode statusCode, ContentType contentType, List<String> extraHeaders,
                       byte[] body) {
    public Response(StatusLine.StatusCode statusCode, ContentType contentType, byte[] body) {
        this(statusCode, contentType, Collections.emptyList(), body);
    }

    public Response(StatusLine.StatusCode statusCode, ContentType contentType, String body) {
        this(statusCode, contentType, Collections.emptyList(), body.getBytes());
    }

    public Response(StatusLine.StatusCode statusCode, ContentType contentType, List<String> extraHeaders) {
        this(statusCode, contentType, extraHeaders, "".getBytes());
    }

    public Response(StatusLine.StatusCode statusCode, List<String> extraHeaders) {
        this(statusCode, ContentType.NONE, extraHeaders, "".getBytes());
    }
}

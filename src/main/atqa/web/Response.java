package atqa.web;

import java.util.Collections;
import java.util.List;

public record Response(StatusLine.StatusCode statusCode, ContentType contentType, List<String> extraHeaders,
                       String body) {
    public Response(StatusLine.StatusCode statusCode, ContentType contentType, String body) {
        this(statusCode, contentType, Collections.emptyList(), body);
    }

    public Response(StatusLine.StatusCode statusCode, ContentType contentType, List<String> extraHeaders) {
        this(statusCode, contentType, extraHeaders, "");
    }

    public Response(StatusLine.StatusCode statusCode, List<String> extraHeaders) {
        this(statusCode, ContentType.NONE, extraHeaders, "");
    }
}

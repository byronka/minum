package atqa.web;

import java.util.Collections;
import java.util.List;

/**
 * Represents an HTTP response.
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
}

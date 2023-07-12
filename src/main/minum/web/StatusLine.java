package minum.web;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static minum.utils.Invariants.mustBeTrue;

/**
 * This class represents the text that is sent back in a {@link Response}
 */
public record StatusLine(StatusCode status, WebEngine.HttpVersion version, String rawValue) {

    /**
     * This is the regex used to analyze a status line sent by the server and
     * read by the client.  Servers will send messages like: "HTTP/1.1 200 OK" or "HTTP/1.1 500 Internal Server Error"
     */
    static final String statusLinePattern = "^HTTP/(1.1|1.0) (\\d{3}) (.*)$";
    static final Pattern statusLineRegex = Pattern.compile(statusLinePattern);

    /**
     * See see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Status">Status Codes</a>
     */
    public enum StatusCode{
        _200_OK(200, "OK"),
        _201_CREATED(201, "CREATED"),

        /**
         * Used a lot after receiving a post response.  The pattern is to
         * receive the post, then redirect to a new page. See <a href="https://en.wikipedia.org/wiki/Post/Redirect/Get">...</a>
         */
        _303_SEE_OTHER(303, "SEE OTHER"),

        _401_UNAUTHORIZED(401, "UNAUTHORIZED"),
        _403_FORBIDDEN(403, "FORBIDDEN"),
        _429_TOO_MANY_REQUESTS(429, "TOO MANY REQUESTS"),
        _404_NOT_FOUND(404, "NOT FOUND"),
        _500_INTERNAL_SERVER_ERROR(500, "INTERNAL SERVER ERROR"),

        /**
         * The null object, meant to represent "no status code"
         */
        NULL(0, "NULL OBJECT")
        ;

        public final int code;
        public final String shortDescription;

        StatusCode(int code, String shortDescription) {
            this.code = code;
            this.shortDescription = shortDescription;
        }

        static StatusCode findByCode(int code) {
            return Arrays.stream(StatusCode.values())
                    .filter(x -> x.code == code)
                    .findFirst()
                    .orElseThrow();
        }
    }

    /**
     * Parses a string value of a status line from an HTTP
     * server.  If the input value is null or empty, we'll
     * return a {@link StatusLine} with null-object values
     */
    public static StatusLine extractStatusLine(String value) {
        if (value == null || value.isBlank()) {
            return new StatusLine(StatusCode.NULL, WebEngine.HttpVersion.NONE, "");
        }
        Matcher mr = StatusLine.statusLineRegex.matcher(value);
        mustBeTrue(mr.matches(), String.format("%s must match the statusLinePattern: %s", value, statusLinePattern));
        String version = mr.group(1);
        WebEngine.HttpVersion httpVersion = switch (version) {
            case "1.1" -> WebEngine.HttpVersion.ONE_DOT_ONE;
            case "1.0" -> WebEngine.HttpVersion.ONE_DOT_ZERO;
            default -> throw new RuntimeException(String.format("HTTP version was not an acceptable value. Given: %s", version));
        };
        StatusCode status = StatusCode.findByCode(Integer.parseInt(mr.group(2)));

        return new StatusLine(status, httpVersion, value);
    }
}

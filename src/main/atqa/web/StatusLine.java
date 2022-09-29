package atqa.web;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static atqa.utils.Invariants.mustBeTrue;

public record StatusLine(StatusCode status, Web.HttpVersion version, String rawValue) {

    /**
     * This is the regex used to analyze a status line sent by the server and
     * read by the client.  Servers will send messages like: "HTTP/1.1 200 OK" or "HTTP/1.1 500 Internal Server Error"
     */
    public static final String statusLinePattern = "^HTTP/(1.1|1.0) (\\d{3}) (.*)$";
    public static final Pattern statusLineRegex = Pattern.compile(statusLinePattern);

    public enum StatusCode{
        _200_OK(200, "OK"),
        _404_NOT_FOUND(404, "NOT FOUND"),

        /**
         * Used a lot after receiving a post response.  The pattern is to
         * receive the post, then redirect to a new page. See <a href="https://en.wikipedia.org/wiki/Post/Redirect/Get">...</a>
         */
        _303_SEE_OTHER(303, "SEE OTHER");

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

    public static StatusLine extractStatusLine(String value) {
        Matcher mr = StatusLine.statusLineRegex.matcher(value);
        mustBeTrue(mr.matches(), String.format("%s must match the statusLinePattern: %s", value, statusLinePattern));
        String version = mr.group(1);
        Web.HttpVersion httpVersion = switch (version) {
            case "1.1" -> Web.HttpVersion.ONE_DOT_ONE;
            case "1.0" -> Web.HttpVersion.ONE_DOT_ZERO;
            default -> throw new RuntimeException(String.format("HTTP version was not an acceptable value. Given: %s", version));
        };
        StatusCode status = StatusCode.findByCode(Integer.parseInt(mr.group(2)));

        return new StatusLine(status, httpVersion, value);
    }
}

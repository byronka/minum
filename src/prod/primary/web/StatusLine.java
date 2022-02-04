package primary.web;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.Invariants.mustBeTrue;

public class StatusLine {

    /**
     * This is the regex used to analyze a status line sent by the server and
     * read by the client.  Servers will send messages like: "HTTP/1.1 200 OK" or "HTTP/1.1 500 Internal Server Error"
     */
    public static final String statusLinePattern = "^HTTP/(1.1|1.0) (\\d{3}) (.*)$";
    public static final Pattern statusLineRegex = Pattern.compile(statusLinePattern);

    public final Status status;
    public final Web.HttpVersion version;
    public final String rawValue;

    public enum Status {
        _200_OK(200);

        private final int statusCode;

        Status(int statusCode) {
            this.statusCode = statusCode;
        }

        static Status findByCode(int code) {
            return Arrays.stream(Status.values())
                    .filter(x -> x.statusCode == code)
                    .findFirst()
                    .orElseThrow();
        }
    }

    public StatusLine(Status status, Web.HttpVersion version, String rawValue) {
        this.status = status;
        this.version = version;
        this.rawValue = rawValue;
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
        Status status = Status.findByCode(Integer.parseInt(mr.group(2)));

        return new StatusLine(status, httpVersion, value);
    }
}

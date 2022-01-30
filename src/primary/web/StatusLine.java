package primary.web;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.Invariants.require;

public class StatusLine {

    /**
     * This is the regex used to analyze a status line sent by the server and
     * read by the client.  Servers will send messages like: "HTTP/1.1 200 OK" or "HTTP/1.1 500 Internal Server Error"
     */
    public static final Pattern statusLineRegex =
            Pattern.compile("^HTTP/(1.1|1.0) (\\d{3}) (.*)$");

    public final Status status;
    public final Web.HttpVersion version;
    public final String rawValue;

    enum Status {
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
        require(mr.matches(), "status line matcher did not match on " + value);
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

package primary.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.Invariants.require;

/**
 * Details extracted from the headers.  For example,
 * is this a keep-alive connection? what is the content-length,
 * and so on.
 */
public class HeaderInformation {

    /**
     * Used for extracting the length of the body, in POSTs and
     * responses from servers
     */
    static Pattern contentLengthRegex = Pattern.compile("^[cC]ontent-[lL]ength: (.*)$");

    public final List<String> rawValues;
    public final int contentLength;


    public HeaderInformation(int contentLength, List<String> rawValues) {
        this.contentLength = contentLength;
        this.rawValues = rawValues;
    }


    /**
     * Loops through reading text lines from the socket wrapper,
     * returning a list of what it has found when it hits an empty line.
     * This is the HTTP convention.
     */
    public static HeaderInformation extractHeaderInformation(Web.SocketWrapper sw) {
        List<String> headers = getAllHeaders(sw);
        int contentLength = extractContentLength(headers);

        return new HeaderInformation(contentLength, headers);
    }

    private static int extractContentLength(List<String> headers) {
        List<String> cl = headers.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith("content-length")).toList();
        require(cl.isEmpty() || cl.size() == 1, "The number of content-length headers must be exactly zero or one");
        int contentLength = 0;
        if (!cl.isEmpty()) {
            Matcher clMatcher = contentLengthRegex.matcher(cl.get(0));
            require(clMatcher.matches(), "The content length header value must match the contentLengthRegex");
            contentLength = Integer.parseInt(clMatcher.group(1));
        }
        return contentLength;
    }

    private static List<String> getAllHeaders(Web.SocketWrapper sw) {
        List<String> headers = new ArrayList<>();
        while (true) {
            String value = sw.readLine();
            if (value.trim().isEmpty()) {
                break;
            } else {
                headers.add(value);
            }
        }
        return headers;
    }
}

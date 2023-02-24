package atqa.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static atqa.utils.Invariants.mustBeTrue;

/**
 * Details extracted from the headers.  For example,
 * is this a keep-alive connection? what is the content-length,
 * and so on.
 */
public record Headers(List<String> headerStrings) {

    /**
     * Used for extracting the length of the body, in POSTs and
     * responses from servers
     */
    static final Pattern contentLengthRegex = Pattern.compile("^[cC]ontent-[lL]ength: (.*)$");

    /**
     * Loops through reading text lines from the socket wrapper,
     * returning a list of what it has found when it hits an empty line.
     * This is the HTTP convention.
     */
    public static Headers extractHeaderInformation(SocketWrapper sw) throws IOException {
        List<String> headers = getAllHeaders(sw);
        return new Headers(headers);
    }

    /**
     * Gets the one content-type header, or returns an empty string
     */
    public String contentType() {
        // find the header that starts with content-type
        List<String> cts = headerStrings().stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith("content-type")).toList();
        mustBeTrue(cts.isEmpty() || cts.size() == 1, "The number of content-type headers must be exactly zero or one");
        if (!cts.isEmpty()) {
            return cts.get(0);
        }

        // if we don't find a content-type header, or if we don't find one we can handle, return an empty string.
        return "";
    }

    /**
     * Given the list of headers, find the one with the length of the
     * body of the POST and return that value as a simple integer
     */
    public int contentLength() {
        List<String> cl = headerStrings().stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith("content-length")).toList();
        mustBeTrue(cl.isEmpty() || cl.size() == 1, "The number of content-length headers must be exactly zero or one");
        int contentLength = 0;
        if (!cl.isEmpty()) {
            Matcher clMatcher = contentLengthRegex.matcher(cl.get(0));
            mustBeTrue(clMatcher.matches(), "The content length header value must match the contentLengthRegex");
            contentLength = Integer.parseInt(clMatcher.group(1));
            mustBeTrue(contentLength >= 0, "Content-length cannot be negative");
        }

        return contentLength;
    }

    private static List<String> getAllHeaders(SocketWrapper sw) throws IOException {
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

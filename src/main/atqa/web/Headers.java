package atqa.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static atqa.Constants.MAX_HEADERS_COUNT;
import static atqa.utils.Invariants.mustBeTrue;
import static atqa.web.InputStreamUtils.readLine;

/**
 * Details extracted from the headers.  For example,
 * is this a keep-alive connection? what is the content-length,
 * and so on.
 */
public record Headers(
        /*
         * Each line of the headers is read into this data structure
         */
        List<String> headerStrings
) {

    public static final Headers EMPTY = new Headers(List.of());

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
    public static Headers extractHeaderInformation(InputStream is) throws IOException {
        List<String> headers = getAllHeaders(is);
        return new Headers(headers);
    }

    /**
     * Obtain any desired header by looking it up in this map
     */
    public Map<String, List<String>> headersAsMap() {
        var result = new HashMap<String, List<String>>();
        for (var h : headerStrings) {
            var indexOfFirstColon = h.indexOf(":");

            // if the header is malformed, just move on
            if (indexOfFirstColon <= 0) continue;

            String key = h.substring(0, indexOfFirstColon);
            String value = h.substring(indexOfFirstColon+1).trim();


            if (result.containsKey(key)) {
                var currentValue = result.get(key);
                currentValue.add(value);
                result.put(key, currentValue);
            } else {
                result.put(key, Arrays.asList(value));
            }

        }
        return result;
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
     * body of the POST and return that value as a simple integer. If
     * we do not find a content length, return -1.
     */
    public int contentLength() {
        List<String> cl = headerStrings().stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith("content-length")).toList();
        mustBeTrue(cl.isEmpty() || cl.size() == 1, "The number of content-length headers must be exactly zero or one");
        int contentLength = -1;
        if (!cl.isEmpty()) {
            Matcher clMatcher = contentLengthRegex.matcher(cl.get(0));
            mustBeTrue(clMatcher.matches(), "The content length header value must match the contentLengthRegex");
            contentLength = Integer.parseInt(clMatcher.group(1));
            mustBeTrue(contentLength >= 0, "Content-length cannot be negative");
        }

        return contentLength;
    }

    private static List<String> getAllHeaders(InputStream is) throws IOException {
        List<String> headers = new ArrayList<>();
        // we'll only grab the first MAX_HEADERS_COUNT headers.
        for (int i = 0; i <= MAX_HEADERS_COUNT; i++) {
            if (i == MAX_HEADERS_COUNT) throw new RuntimeException("User tried sending too many headers.  Current max: " + MAX_HEADERS_COUNT);
            String value = readLine(is);
            if (value != null && value.isBlank()) {
                break;
            } else {
                headers.add(value);
            }
        }
        return headers;
    }
}

package minum.web;

import minum.Constants;
import minum.Context;
import minum.exceptions.ForbiddenUseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static minum.utils.Invariants.mustBeTrue;

/**
 * Details extracted from the headers.  For example,
 * is this a keep-alive connection? what is the content-length,
 * and so on.
 * Here is some detail from <a href="https://en.wikipedia.org/wiki/List_of_HTTP_header_fields">Wikipedia</a> on the subject:
 * <p>
 * HTTP header fields are a list of strings sent and received by both
 * the client program and server on every HTTP request and response. These
 * headers are usually invisible to the end-user and are only processed or
 * logged by the server and client applications. They define how information
 * sent/received through the connection are encoded (as in Content-Encoding),
 * the session verification and identification of the client (as in browser
 * cookies, IP address, user-agent) or their anonymity thereof (VPN or
 * proxy masking, user-agent spoofing), how the server should handle data
 * (as in Do-Not-Track), the age (the time it has resided in a shared cache)
 * of the document being downloaded, amongst others.
 * </p>
 */
public class Headers{

    private static InputStreamUtils inputStreamUtils;
    /**
     * Each line of the headers is read into this data structure
     */
    private final List<String> headerStrings;
    private final Constants constants;
    private final Context context;
    private final Map<String, List<String>> headersMap;


    public Headers(
            List<String> headerStrings,
            Context context
    ) {
        this.context = context;
        this.constants = context.getConstants();
        this.headerStrings = headerStrings;
        this.headersMap = Collections.unmodifiableMap(extractHeadersToMap());
    }

    public List<String> getHeaderStrings() {
        return headerStrings;
    }

    /**
     * Used for extracting the length of the body, in POSTs and
     * responses from servers
     */
    private final Pattern contentLengthRegex = Pattern.compile("^[cC]ontent-[lL]ength: (.*)$");

    public static Headers make(Context context, InputStreamUtils inputStreamUtils) {
        Headers.inputStreamUtils = inputStreamUtils;
        return new Headers(List.of(), context);
    }

    /**
     * Loops through reading text lines from the socket wrapper,
     * returning a list of what it has found when it hits an empty line.
     * This is the HTTP convention.
     */
    public Headers extractHeaderInformation(InputStream is) throws IOException {
        List<String> headers = getAllHeaders(is);
        return new Headers(headers, context);
    }

    /**
     * Obtain any desired header by looking it up in this map.  All keys
     * are made lowercase.
     */
    private Map<String, List<String>> extractHeadersToMap() {
        var result = new HashMap<String, List<String>>();
        for (var h : headerStrings) {
            var indexOfFirstColon = h.indexOf(":");

            // if the header is malformed, just move on
            if (indexOfFirstColon <= 0) continue;

            String key = h.substring(0, indexOfFirstColon).toLowerCase(Locale.ROOT);
            String value = h.substring(indexOfFirstColon+1).trim();

            if (result.containsKey(key)) {
                var currentValue = result.get(key);
                List<String> newList = new ArrayList<>();
                newList.add(value);
                newList.addAll(currentValue);
                result.put(key, newList);
            } else {
                result.put(key, List.of(value));
            }

        }
        return result;
    }

    /**
     * Gets the one content-type header, or returns an empty string
     */
    public String contentType() {
        // find the header that starts with content-type
        List<String> cts = headerStrings.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith("content-type")).toList();
        mustBeTrue(cts.isEmpty() || cts.size() == 1, "The number of content-type headers must be exactly zero or one");
        if (!cts.isEmpty()) {
            return cts.get(0);
        }

        // if we don't find a content-type header, or if we don't find one we can handle, return an empty string.
        return "";
    }

    /**
     * Given the list of headers, find the one with the length of the
     * body of the POST and return that value as an integer. If
     * we do not find a content length, return -1.
     */
    public int contentLength() {
        List<String> cl = headerStrings.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith("content-length")).toList();
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

    /**
     * Indicates whether the headers in this request
     * have a Connection: Keep-Alive
     */
    public boolean hasKeepAlive() {
        List<String> connectionHeader = headersMap.get("connection");
        if (connectionHeader == null) return false;
        return connectionHeader.stream().anyMatch(x -> x.toLowerCase().contains("keep-alive"));
    }

    /**
     * Indicates whether the headers in this request
     * have a Connection: close
     */
    public boolean hasConnectionClose() {
        List<String> connectionHeader = headersMap.get("connection");
        if (connectionHeader == null) return false;
        return connectionHeader.stream().anyMatch(x -> x.toLowerCase().contains("close"));
    }

    private List<String> getAllHeaders(InputStream is) throws IOException {
        List<String> headers = new ArrayList<>();
        // we'll only grab the first MAX_HEADERS_COUNT headers.
        for (int i = 0; i <= constants.MAX_HEADERS_COUNT; i++) {
            if (i == constants.MAX_HEADERS_COUNT) throw new ForbiddenUseException("User tried sending too many headers.  Current max: " + constants.MAX_HEADERS_COUNT);
            String value = inputStreamUtils.readLine(is);
            if (value != null && value.isBlank()) {
                break;
            } else {
                headers.add(value);
            }
        }
        return headers;
    }

    /**
     * Allows a user to obtain any header value by its key.
     */
    public List<String> valueByKey(String key) {
        return headersMap.get(key.toLowerCase(Locale.ROOT));
    }

    public Map<String, List<String>> getHeadersMap() {
        return headersMap;
    }
}

package com.renomad.minum.web;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ForbiddenUseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

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
public final class Headers{

    public static final Headers EMPTY = new Headers(List.of(), null);
    private static final int MAX_HEADERS_COUNT = 70;
    private Integer contentLength;

    /**
     * Each line of the headers is read into this data structure
     */
    private final List<String> headerStrings;
    private final Map<String, List<String>> headersMap;
    private final ILogger logger;

    public Headers(List<String> headerStrings, ILogger logger) {
        this.headerStrings = new ArrayList<>(headerStrings);
        this.headersMap = Collections.unmodifiableMap(extractHeadersToMap(headerStrings));
        this.logger = logger;
    }

    public Headers(List<String> headerStrings) { this(headerStrings, null); }

    public List<String> getHeaderStrings() {
        return new ArrayList<>(headerStrings);
    }

    /**
     * Obtain any desired header by looking it up in this map.  All keys
     * are made lowercase.
     */
    static Map<String, List<String>> extractHeadersToMap(List<String> headerStrings) {
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
        List<String> cts = Objects.requireNonNullElse(headersMap.get("content-type"), List.of());
        if (cts.size() > 1) {
            cts.sort(Comparator.naturalOrder());
            throw new WebServerException("The number of content-type headers must be exactly zero or one.  Received: " + cts);
        }
        if (!cts.isEmpty()) {
            return cts.getFirst();
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
        // if we have a saved value for content length, use that
        if (contentLength != null) return contentLength;

        List<String> cl = Objects.requireNonNullElse(headersMap.get("content-length"), List.of());
        if (cl.isEmpty()) {
            contentLength = -1;
        } else if (cl.size() > 1) {
            if (logger != null) logger.logDebug(() -> "Did not receive a valid content length.  Setting length to -1.  Received: " + cl);
            contentLength = -1;
        } else {
            contentLength = Integer.parseInt(cl.getFirst());
            if (contentLength < 0) {
                if (logger != null) logger.logDebug(() -> "Content length cannot be negative.  Setting length to -1.  Received: " + contentLength);
                contentLength = -1;
            }
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
        return connectionHeader.stream().anyMatch(x -> x.toLowerCase(Locale.ROOT).contains("keep-alive"));
    }

    /**
     * Indicates whether the headers in this request
     * have a Connection: close
     */
    public boolean hasConnectionClose() {
        List<String> connectionHeader = headersMap.get("connection");
        if (connectionHeader == null) return false;
        return connectionHeader.stream().anyMatch(x -> x.toLowerCase(Locale.ROOT).contains("close"));
    }

    /**
     * Loop through the lines of header in the HTTP message
     */
    static List<String> getAllHeaders(InputStream is, IInputStreamUtils inputStreamUtils) {
        // we'll give the list an initial size, since in most cases we're going to have headers.
        // 10 is just an arbitrary number, seems about right.
        List<String> headers = new ArrayList<>(10);
        for (int i = 0;; i++) {
            if (i >=MAX_HEADERS_COUNT) {
                throw new ForbiddenUseException("User tried sending too many headers.  max: " + MAX_HEADERS_COUNT);
            }
            String value;
            try {
                value = inputStreamUtils.readLine(is);
            } catch (IOException e) {
                throw new WebServerException(e);
            }
            if (value != null && value.isBlank()) {
                break;
            } else if (value == null) {
                return headers;
            } else {
                headers.add(value);
            }
        }
        return headers;
    }

    /**
     * Allows a user to obtain any header value by its key, case-insensitively
     * @return a {@link List} of string values, or null
     * if no header was found.
     */
    public List<String> valueByKey(String key) {
        return headersMap.get(key.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Headers headers = (Headers) o;
        return Objects.equals(contentLength, headers.contentLength) && Objects.equals(headerStrings, headers.headerStrings) && Objects.equals(headersMap, headers.headersMap) && Objects.equals(logger, headers.logger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentLength, headerStrings, headersMap, logger);
    }

    @Override
    public String toString() {
        return "Headers{" +
                "headerStrings=" + headerStrings +
                '}';
    }
}

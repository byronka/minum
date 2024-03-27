package com.renomad.minum.web;

import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.exceptions.ForbiddenUseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.renomad.minum.utils.Invariants.mustBeTrue;

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

    public static final Headers EMPTY = new Headers(List.of(), Context.EMPTY);
    private final IInputStreamUtils inputStreamUtils;
    /**
     * Each line of the headers is read into this data structure
     */
    private final List<String> headerStrings;
    private final Constants constants;
    private final Context context;
    private final Map<String, List<String>> headersMap;


    /**
     * It is rare you will use this constructor.  Instead, see {@link #make(Context)}
     * when you have to extract the headers from a live {@link InputStream}
     */
    public Headers(
            List<String> headerStrings,
            Context context
    ) {
        this.context = context;
        inputStreamUtils = context.getInputStreamUtils();
        this.constants = context.getConstants();
        this.headerStrings = headerStrings;
        this.headersMap = Collections.unmodifiableMap(extractHeadersToMap(headerStrings));
    }

    public List<String> getHeaderStrings() {
        return headerStrings;
    }

    /**
     * Used for extracting the length of the body, in POSTs and
     * responses from servers
     */
    private static final Pattern contentLengthRegex = Pattern.compile("^[cC]ontent-[lL]ength: (.*)$");

    /**
     * Run this command to build a Headers object.
     */
    public static Headers make(Context context) {
        return new Headers(List.of(), context);
    }

    /**
     * Loops through reading text lines from the socket wrapper,
     * returning a list of what it has found when it hits an empty line.
     * This is the HTTP convention.
     */
    public Headers extractHeaderInformation(InputStream is) {
        List<String> headers = getAllHeaders(is, constants.maxHeadersCount, inputStreamUtils);
        return new Headers(headers, context);
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
        List<String> cts = headerStrings.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith("content-type")).toList();
        if (cts.size() > 1) {
            throw new WebServerException("The number of content-type headers must be exactly zero or one.  Recieved: " + cts);
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
        List<String> cl = headerStrings.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith("content-length")).toList();
        if (cl.size() > 1) {
            throw new WebServerException("The number of content-length headers must be exactly zero or one.  Received: " + cl);
        }
        int contentLength = -1;
        if (!cl.isEmpty()) {
            Matcher clMatcher = contentLengthRegex.matcher(cl.getFirst());
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

    /**
     * Loop through the lines of header in the HTTP message
     */
    static List<String> getAllHeaders(InputStream is, int maxHeadersCount, IInputStreamUtils inputStreamUtils) {
        List<String> headers = new ArrayList<>();
        for (int i = 0;; i++) {
            if (i >=maxHeadersCount) {
                throw new ForbiddenUseException("User tried sending too many headers.  Current max: " + maxHeadersCount);
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
}

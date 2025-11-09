package com.renomad.minum.web;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.utils.StringUtils;

import java.util.*;

import static com.renomad.minum.utils.Invariants.mustNotBeNull;

/**
 * This class holds data and methods for dealing with the
 * "start line" in an HTTP request.  For example,
 * GET /foo HTTP/1.1
 */
public final class RequestLine {

    private final Method method;
    private final PathDetails pathDetails;
    private final HttpVersion version;
    private final String rawValue;
    private final ILogger logger;
    static final int MAX_QUERY_STRING_KEYS_COUNT = 50;

    /**
     * @param method GET, POST, etc.
     * @param pathDetails See {@link PathDetails}
     * @param version the version of HTTP (1.0 or 1.1) we're receiving
     * @param rawValue the entire raw string of the start line
     */
    public RequestLine(
            Method method,
            PathDetails pathDetails,
            HttpVersion version,
            String rawValue,
            ILogger logger
    ) {
        this.method = method;
        this.pathDetails = pathDetails;
        this.version = version;
        this.rawValue = rawValue;
        this.logger = logger;
    }



    public static final RequestLine EMPTY = new RequestLine(Method.NONE, PathDetails.empty, HttpVersion.NONE, "", null);

    /**
     * Returns a map of the key-value pairs in the URL,
     * for example in {@code http://foo.com?name=alice} you
     * have a key of name and a value of alice.
     */
    public Map<String, String> queryString() {
        if (pathDetails == null || pathDetails.getQueryString().isEmpty()) {
            return Map.of();
        } else {
            return new HashMap<>(pathDetails.getQueryString());
        }

    }

    /**
     * These are the HTTP methods we handle.
     */
    public enum Method {
        GET,
        POST,
        PUT,
        DELETE,
        TRACE,
        PATCH,
        OPTIONS,
        HEAD,

        /**
         * Represents the null value of Method
         */
        NONE;

        /**
         * Get the correct HTTP method for a string provided by the user
         */
        public static Method getMethod(String userSuppliedMethod) {
            // if the method they gave us is longer than the maximum method
            // we know about, it's invalid, and return NONE.
            if (userSuppliedMethod.length() > 7) {
                return NONE;
            }
            // necessary because we need to potentially convert to lowercase
            var sb = new StringBuilder(userSuppliedMethod.length());

            for (int i = 0; i < userSuppliedMethod.length(); i++) {
                char c = userSuppliedMethod.charAt(i);
                if ((c >= 65 && c <= 90)) { // characters in methods are pure ascii, no UTF-8 to worry about
                    sb.append(c);
                } else if (c >= 97 && c <= 122) { // if characters are lower-case, make them upper-case
                    sb.append((char)(c - 32));
                } else {
                    return NONE; // if any of the characters are non-ascii-alphabet, it's invalid, bail.
                }
            }
            return switch (sb.toString()) {
                case "GET" -> GET;
                case "POST" -> POST;
                case "PUT" -> PUT;
                case "DELETE" -> DELETE;
                case "TRACE" -> TRACE;
                case "PATCH" -> PATCH;
                case "OPTIONS" -> OPTIONS;
                case "HEAD" -> HEAD;
                default -> NONE;
            };
        }
    }

    /**
     * Given the string value of a Request Line (like GET /hello HTTP/1.1)
     * validate and extract the values for our use.
     */
    public RequestLine extractRequestLine(String value) {
        mustNotBeNull(value);
        if (value.isEmpty()) {
            return RequestLine.EMPTY;
        }
        RequestLineRawValues rawValues = requestLineTokenizer(value);
        if (rawValues == null) {
            return RequestLine.EMPTY;
        }
        Method myMethod;
        myMethod = Method.getMethod(rawValues.method());
        if (myMethod.equals(Method.NONE)) {
            logger.logDebug(() -> "Unable to convert method to enum.  Returning empty request line.  Method value provided: " + rawValues.method());
            return RequestLine.EMPTY;
        }
        PathDetails pd = extractPathDetails(rawValues.path());
        HttpVersion httpVersion = getHttpVersion(rawValues.protocol());
        if (httpVersion.equals(HttpVersion.NONE)) {
            return RequestLine.EMPTY;
        }

        return new RequestLine(myMethod, pd, httpVersion, value, logger);
    }

    /**
     * Split the request line into three parts - a method (e.g. GET), a
     * path (e.g. "/" or "/helloworld/hi/foo?name=hello") and a protocol,
     * which is typically "HTTP/1.1" but might be "HTTP/1.0" in some cases
     * <br>
     * If we don't find exactly three parts, we will return null, which
     * is interpreted by the calling method to mean we didn't receive a
     * valid request line.
     * @param rawRequestLine the full string of the first line received
     *                       after the socket is connected to the client.
     */
    private RequestLineRawValues requestLineTokenizer(String rawRequestLine) {
        int firstSpace = rawRequestLine.indexOf(' ');
        if (firstSpace == -1) {
            return null;
        }
        int secondSpace = rawRequestLine.indexOf(' ', firstSpace + 1);
        if (secondSpace == -1) {
            return null;
        }
        int thirdSpace = rawRequestLine.indexOf(' ', secondSpace + 1);
        if (thirdSpace != -1) {
            return null;
        }
        String myMethod = rawRequestLine.substring(0, firstSpace);
        String path = rawRequestLine.substring(firstSpace + 1, secondSpace);
        String protocol = rawRequestLine.substring(secondSpace + 1);
        return new RequestLineRawValues(myMethod, path, protocol);
    }

    private PathDetails extractPathDetails(String path) {
        PathDetails pd;
        // the request line will have a forward slash at the beginning of
        // the path.  Remove that here.
        String adjustedPath = path.substring(1);
        int locationOfQueryBegin = adjustedPath.indexOf("?");
        if (locationOfQueryBegin >= 0) {
            // in this case, we found a question mark, suggesting that a query string exists
            String rawQueryString = adjustedPath.substring(locationOfQueryBegin + 1);
            String isolatedPath = adjustedPath.substring(0, locationOfQueryBegin);
            Map<String, String> queryString = extractMapFromQueryString(rawQueryString);
            pd = new PathDetails(isolatedPath, rawQueryString, queryString);
        } else {
            // in this case, no question mark was found, thus no query string
            pd = new PathDetails(adjustedPath, null, null);
        }
        return pd;
    }


    /**
     * Given a string containing the combined key-values in
     * a query string (e.g. foo=bar&name=alice), split that
     * into a map of the key to value (e.g. foo to bar, and name to alice)
     */
    Map<String, String> extractMapFromQueryString(String rawQueryString) {
        Map<String, String> queryStrings = new HashMap<>();
        StringTokenizer tokenizer = new StringTokenizer(rawQueryString, "&");
        // we'll only take less than MAX_QUERY_STRING_KEYS_COUNT
        for (int i = 0; tokenizer.hasMoreTokens(); i++) {
            if (i >= MAX_QUERY_STRING_KEYS_COUNT) throw new ForbiddenUseException("User tried providing too many query string keys.  max: " + MAX_QUERY_STRING_KEYS_COUNT);
            // this should give us a key and value joined with an equal sign, e.g. foo=bar
            String currentKeyValue = tokenizer.nextToken();
            int equalSignLocation = currentKeyValue.indexOf("=");
            if (equalSignLocation <= 0) return Map.of();
            String key = currentKeyValue.substring(0, equalSignLocation);
            String myRawValue = currentKeyValue.substring(equalSignLocation + 1);
            try {
                String value = StringUtils.decode(myRawValue);
                queryStrings.put(key, value);
            } catch (IllegalArgumentException ex) {
                logger.logDebug(() -> "Query string parsing failed for key: (%s) value: (%s).  Skipping to next key-value pair. error message: %s".formatted(key, myRawValue, ex.getMessage()));
            }
        }
        return queryStrings;
    }

    /**
     * Extract the HTTP version from the start line
     */
    private HttpVersion getHttpVersion(String version) {
        if (version.equals("HTTP/1.1")) {
            return HttpVersion.ONE_DOT_ONE;
        } else if (version.equals("HTTP/1.0")) {
            return HttpVersion.ONE_DOT_ZERO;
        } else {
            return HttpVersion.NONE;
        }
    }

    /**
     * Return the method of this request-line.  For example, GET, PUT, POST...
     */
    public Method getMethod() {
        return method;
    }

    /**
     * This returns an object which contains essential information about the path
     * in the request line.  For example, if the request line is "GET /sample?foo=bar HTTP/1.1",
     * this would hold data for the path ("sample") and the query string ("foo=bar")
     */
    public PathDetails getPathDetails() {
        return pathDetails;
    }

    /**
     * Gets the HTTP version, either 1.0 or 1.1
     */
    public HttpVersion getVersion() {
        return this.version;
    }

    /**
     * Get the string value of this request line, such as "GET /sample.html HTTP/1.1"
     */
    public String getRawValue() {
        return rawValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestLine that = (RequestLine) o;
        return method == that.method && Objects.equals(pathDetails, that.pathDetails) && version == that.version && Objects.equals(rawValue, that.rawValue) && Objects.equals(logger, that.logger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, pathDetails, version, rawValue, logger);
    }

    @Override
    public String toString() {
        return "RequestLine{" +
                "method=" + method +
                ", pathDetails=" + pathDetails +
                ", version=" + version +
                ", rawValue='" + rawValue + '\'' +
                ", logger=" + logger +
                '}';
    }
}

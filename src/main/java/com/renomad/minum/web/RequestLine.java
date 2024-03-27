package com.renomad.minum.web;

import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.exceptions.ForbiddenUseException;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.renomad.minum.utils.Invariants.mustBeTrue;
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
    private final Context context;
    private final Constants constants;
    private final ILogger logger;

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
            Context context
    ) {
        this.method = method;
        this.pathDetails = pathDetails;
        this.version = version;
        this.rawValue = rawValue;
        this.context = context;
        this.constants = context.getConstants();
        this.logger = context.getLogger();
    }

    /**
     * This is our regex for looking at a client's request
     * and determining what to send them.  For example,
     * if they send GET /sample.html HTTP/1.1, we send them sample.html
     * <p>
     * On the other hand if it's not a well-formed request, or
     * if we don't have that file, we reply with an error page
     * </p>
     */
    static final String REQUEST_LINE_PATTERN = "^([A-Z]{3,8})" + // an HTTP method, like GET, HEAD, POST, or OPTIONS
            " /?(.*)" + // the request target - may or may not start with a slash.
            " HTTP/(1.1|1.0)$"; // the HTTP version, defining structure of the remaining message

    static final Pattern startLineRegex = Pattern.compile(REQUEST_LINE_PATTERN);

    public static RequestLine empty(Context context) {
        return new RequestLine(Method.NONE, PathDetails.empty, HttpVersion.NONE, "", context);
    }

    /**
     * Returns a map of the key-value pairs in the URL,
     * for example in {@code http://foo.com?name=alice} you
     * have a key of name and a value of alice.
     */
    public Map<String, String> queryString() {
        if (pathDetails == null || pathDetails.queryString == null || pathDetails.queryString.isEmpty()) {
            return new HashMap<>();
        } else {
            return new HashMap<>(pathDetails.queryString);
        }

    }

    /**
     * These are the HTTP methods we handle.
     * @see #REQUEST_LINE_PATTERN
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
        NONE
    }

    /**
     * Given the string value of a Request Line (like GET /hello HTTP/1.1)
     * validate and extract the values for our use.
     */
    public RequestLine extractRequestLine(String value) {
        mustNotBeNull(value);
        Matcher m = RequestLine.startLineRegex.matcher(value);
        // run the regex
        var doesMatch = m.matches();
        if (!doesMatch) {
            return RequestLine.empty(context);
        }
        mustBeTrue(doesMatch, String.format("%s must match the startLinePattern: %s", value, REQUEST_LINE_PATTERN));
        Method myMethod = extractMethod(m.group(1));
        PathDetails pd = extractPathDetails(m.group(2));
        HttpVersion httpVersion = getHttpVersion(m.group(3));

        return new RequestLine(myMethod, pd, httpVersion, value, context);
    }

    private Method extractMethod(String methodString) {
        try {
            return Method.valueOf(methodString.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            logger.logDebug(() -> "Unable to convert method to enum: " + methodString);
            return Method.NONE;
        }
    }

    private PathDetails extractPathDetails(String path) {
        PathDetails pd;
        int locationOfQueryBegin = path.indexOf("?");
        if (locationOfQueryBegin > 0) {
            // in this case, we found a question mark, suggesting that a query string exists
            String rawQueryString = path.substring(locationOfQueryBegin + 1);
            String isolatedPath = path.substring(0, locationOfQueryBegin);
            Map<String, String> queryString = extractMapFromQueryString(rawQueryString);
            pd = new PathDetails(isolatedPath, rawQueryString, queryString);
        } else {
            // in this case, no question mark was found, thus no query string
            pd = new PathDetails(path, null, null);
        }
        return pd;
    }

    /**
     * Some essential characteristics of the path portion of the start line
     * @param isolatedPath the isolated path is found after removing the query string
     * @param rawQueryString the raw query is the string after a question mark (if it exists - it's optional)
     *                       if there is no query string, then we leave rawQuery as a null value
     * @param queryString the query is a map of the keys -> values found in the query string
     */
    public record PathDetails (
        String isolatedPath,
        String rawQueryString,
        Map<String, String> queryString
    ){
        public static final PathDetails empty = new PathDetails("", "", Map.of());
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
            if (i >= constants.maxQueryStringKeysCount) throw new ForbiddenUseException("User tried providing too many query string keys.  Current max: " + constants.maxQueryStringKeysCount);
            // this should give us a key and value joined with an equal sign, e.g. foo=bar
            String currentKeyValue = tokenizer.nextToken();
            int equalSignLocation = currentKeyValue.indexOf("=");
            if (equalSignLocation <= 0) return Map.of();
            String key = currentKeyValue.substring(0, equalSignLocation);
            String value = StringUtils.decode(currentKeyValue.substring(equalSignLocation + 1));
            queryStrings.put(key, value);
        }
        return queryStrings;
    }

    /**
     * Extract the HTTP version from the start line
     */
    private HttpVersion getHttpVersion(String version) {
        if (version.equals("1.1")) {
            return HttpVersion.ONE_DOT_ONE;
        } else {
            return HttpVersion.ONE_DOT_ZERO;
        }
    }

    public Method getMethod() {
        return method;
    }

    public PathDetails getPathDetails() {
        return pathDetails;
    }

    public HttpVersion getVersion() {
        return this.version;
    }

    public String getRawValue() {
        return rawValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestLine that = (RequestLine) o;
        return method == that.method && Objects.equals(pathDetails, that.pathDetails) && version == that.version && Objects.equals(rawValue, that.rawValue) && Objects.equals(context, that.context) && Objects.equals(constants, that.constants) && Objects.equals(logger, that.logger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, pathDetails, version, rawValue, context, constants, logger);
    }

    @Override
    public String toString() {
        return "StartLine{" +
                "method=" + method +
                ", pathDetails=" + pathDetails +
                ", version=" + version +
                ", rawValue='" + rawValue + '\'' +
                '}';
    }
}

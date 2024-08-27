package com.renomad.minum.web;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Some essential characteristics of the path portion of the start line
 */
public final class PathDetails {

    public static final PathDetails empty = new PathDetails("", "", Map.of());

    private final String isolatedPath;
    private final String rawQueryString;
    private final Map<String, String> queryString;

    /**
     * Basic constructor
     * @param isolatedPath the isolated path is found after removing the query string
     * @param rawQueryString the raw query is the string after a question mark (if it exists - it's optional)
     *                       if there is no query string, then we leave rawQuery as a null value
     * @param queryString the query is a map of the keys -> values found in the query string
     */
    public PathDetails (
            String isolatedPath,
            String rawQueryString,
            Map<String, String> queryString
    ) {
        this.isolatedPath = isolatedPath;
        this.rawQueryString = rawQueryString;
        this.queryString = new HashMap<>(queryString == null ? Map.of() : queryString);
    }

    /**
     * Provides the path by itself, without the query string.  For examples,
     * here are some request lines with their isolated paths:
     * <pre>
     * {@code
     * request line                                isolated path
     * -------------------                         -------------
     * POST / HTTP/1.1                             ""
     * GET /background.png HTTP/1.0                "background.png"
     * HEAD /test.html?query=alibaba HTTP/1.1      "test.html"
     * OPTIONS /anypage.html HTTP/1.0              "anypage.html"
     * }
     * </pre>
     */
    public String getIsolatedPath() {
        return isolatedPath;
    }

    /**
     * Returns the raw query string.  For example, in "HEAD /test.html?query=alibaba HTTP/1.1",
     * the raw query string is "query=alibaba"
     */
    public String getRawQueryString() {
        return rawQueryString;
    }

    /**
     * This returns the query string portion of the request line as a map, with
     * case-sensitive keys.
     */
    public Map<String, String> getQueryString() {
        return new HashMap<>(queryString);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PathDetails that)) return false;
        return Objects.equals(isolatedPath, that.isolatedPath) && Objects.equals(rawQueryString, that.rawQueryString) && Objects.equals(queryString, that.queryString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isolatedPath, rawQueryString, queryString);
    }

    @Override
    public String toString() {
        return "PathDetails{" +
                "isolatedPath='" + isolatedPath + '\'' +
                ", rawQueryString='" + rawQueryString + '\'' +
                ", queryString=" + queryString +
                '}';
    }
}
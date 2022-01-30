package primary.web;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static primary.web.HttpUtils.decode;
import static utils.Invariants.require;

/**
 * This class holds data and methods for dealing with the
 * "start line" in an HTTP request.  For example,
 * GET /foo HTTP/1.1
 */
public class StartLine {

    /**
     * This is our regex for looking at a client's request
     * and determining what to send them.  For example,
     * if they send GET /sample.html HTTP/1.1, we send them sample.html
     * <p>
     * On the other hand if it's not a well-formed request, or
     * if we don't have that file, we reply with an error page
     */
    public static final Pattern startLineRegex =
            Pattern.compile("^(GET|POST) /(.*) HTTP/(1.1|1.0)$");

    public final Verb verb;
    public final String path;
    public final Web.HttpVersion version;

    /**
     * The raw value given by the server for status
     */
    public final String rawValue;
    private final String rawQueryString;
    private final Map<String, String> queryString;

    public Map<String, String> getQueryString() {
        return new HashMap<>(queryString);
    }

    enum Verb {
        GET, POST;
    }

    public StartLine(Verb verb, String path, Web.HttpVersion version, String rawQueryString, Map<String, String> queryString, String rawStartLine) {
        this.verb = verb;
        this.path = path;
        this.version = version;
        this.rawQueryString = rawQueryString;
        this.queryString = queryString;
        this.rawValue = rawStartLine;
    }

    public static StartLine extractStartLine(String value) {
        Matcher m = StartLine.startLineRegex.matcher(value);
        require(m.matches(), String.format("%s must match the startLineRegex", value));

        Verb verb = extractVerb(m.group(1));
        PathDetails pd = extractPathDetails(m.group(2));
        Web.HttpVersion httpVersion = getHttpVersion(m.group(3));

        return new StartLine(verb, pd.isolatedPath, httpVersion, pd.rawQuery, pd.query, value);
    }

    private static Verb extractVerb(String verbString) {
        return Verb.valueOf(verbString.toUpperCase(Locale.ROOT));
    }

    private static PathDetails extractPathDetails(String path) {
        PathDetails pd = new PathDetails();
        int locationOfQueryBegin = path.indexOf("?");
        if (locationOfQueryBegin > 0) {
            // in this case, we found a question mark, suggesting that a query string exists
            pd.rawQuery = path.substring(locationOfQueryBegin + 1);
            pd.isolatedPath = path.substring(0, locationOfQueryBegin);
            pd.query = extractMapFromQueryString(pd.rawQuery);
        } else {
            // in this case, no question mark was found, thus no query string
            pd.rawQuery = null;
            pd.isolatedPath = path;
        }
        return pd;
    }

    /**
     * Some essential characteristics of the path portion of the start line
     */
    private static class PathDetails {
        // the isolated path is found after removing the query string
        String isolatedPath;

        // the raw query is the string after a question mark (if it exists - it's optional)
        // if there is no query string, then we leave rawQuery as a null value
        String rawQuery;

        // the query is a map of the keys -> values found in the query string
        Map<String, String> query;
    }

    /**
     * Given a string containing the combined key-values in
     * a query string (e.g. foo=bar&name=alice), split that
     * into a map of the key to value (e.g. foo to bar, and name to alice)
     */
    private static Map<String, String> extractMapFromQueryString(String rawQueryString) {
        Map<String, String> queryStrings = new HashMap<>();
        StringTokenizer tokenizer = new StringTokenizer(rawQueryString, "&");
        while (tokenizer.hasMoreTokens()) {
            // this should give us a key and value joined with an equal sign, e.g. foo=bar
            String currentKeyValue = tokenizer.nextToken();
            int equalSignLocation = currentKeyValue.indexOf("=");
            require(equalSignLocation > 0, "There must be an equals sign");
            String key = currentKeyValue.substring(0, equalSignLocation);
            String value = decode(currentKeyValue.substring(equalSignLocation + 1));
            queryStrings.put(key, value);
        }
        return queryStrings;
    }

    /**
     * Extract the HTTP version from the start line
     */
    private static Web.HttpVersion getHttpVersion(String version) {
        Web.HttpVersion httpVersion = switch (version) {
            case "1.1" -> Web.HttpVersion.ONE_DOT_ONE;
            case "1.0" -> Web.HttpVersion.ONE_DOT_ZERO;
            default -> throw new RuntimeException(String.format("HTTP version was not an acceptable value. Given: %s", version));
        };
        return httpVersion;
    }

}

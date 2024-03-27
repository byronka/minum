package com.renomad.minum.testing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handy helpers to make regular expression marginally
 * easier / more efficient, etc.
 */
public final class RegexUtils {

    /**
     * Helper to find a value in a string using a
     * Regex. Note, this is not nearly as performant, since
     * each call to this method will compile the regular
     * expression.
     * @return returns the first match found, or an empty string
     */
    public static String find(String regex, String data) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(data);
        return matcher.find() ? matcher.group(0) : "";
    }

    private RegexUtils() {}

    /**
     * Returns whether the regular expression matched the data
     * Note: This is a poor-performance method, mainly used
     * as a quick helper where performance concerns don't exist,since
     * each call to this method will compile the regular
     * expression.
     */
    public static boolean isFound(String regex, String data) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(data);
        return matcher.find();
    }

    /**
     * Find a value by regular expression, for testing
     * <p>
     * A helper method to make things it easier to find a value in a string using a
     * Regex. <em>This method is slow, since
     * each call will compile the regular
     * expression.</em>
     * </p>
     * <p>
     *     This version is similar to {@link #find(String, String)} except
     *     that it allows you to specify a match group by name.
     *     For example, here's a regex with a named match group,
     *     in this example the name is "namevalue":
     * </p>
     * <p>
     *     <pre>
     *         {@code "\\bname\\b=\"(?<namevalue>.*?)\""}
     *     </pre>
     * </p>
     * <p>
     *     Thus, to use it here, you would search like this:
     * </p>
     * <p>
     *     <pre>
     *         {@code find("\\bname\\b=\"(?<namevalue>.*?)\"", data, "namevalue")}
     *     </pre>
     * </p>
     * <p>
     *     To summarize: in a regex, you specify a matching group by
     *     surrounding it with parentheses.  To name it, you insert
     *     right after the opening parenthesis a question mark and
     *     then a string literal surrounded by angle brackets
     * </p>
     * <p><b>Important</b>: the name of the match group must be alphanumeric - do
     * <b>not</b> use any special characters or punctuation</p>
     * @return returns the first match found, or an empty string
     */
    public static String find(String regex, String data, String matchGroupName) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(data);
        return matcher.find() ? matcher.group(matchGroupName) : "";
    }
}

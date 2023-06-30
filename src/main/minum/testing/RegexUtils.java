package minum.testing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handy helpers to make regular expression marginally
 * easier / more efficient, etc.
 */
public class RegexUtils {

    /**
     * Makes it a bit easier to find a value in a string using a
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
}

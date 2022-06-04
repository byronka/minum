package utils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static utils.Invariants.mustNotBeNull;

/**
 * Some simple helper methods for Strings.
 */
public class StringUtils {

    private StringUtils() {
        // using a private constructor to hide the implicit public one.
    }

    /**
     * checks the String you pass in; if it's null, return an empty String.
     * Otherwise, return the unchanged string.
     */
    public static String makeNotNull(String s) {
        return s == null ? "" : s;
    }

    /**
     * Encodes UTF-8 text using URL-encoding
     */
    public static String encode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }

    /**
     * Decodes URL-encoded UTF-8 text
     */
    public static String decode(String str) {
        mustNotBeNull(str);
        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }

    /**
     * Similar to {@link #decode} except that we
     * first check if the string value is the token %NULL%,
     * which is our way to signify null.
     */
    public static String decodeWithNullToken(String str) {
        mustNotBeNull(str);
        if (str.equals("%NULL%")) {
            return null;
        }
        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }


}


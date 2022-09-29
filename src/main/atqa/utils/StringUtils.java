package atqa.utils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static atqa.utils.Invariants.mustNotBeNull;

/**
 * Some simple helper methods for Strings.
 */
public class StringUtils {

    private StringUtils() {
        // using a private constructor to hide the implicit public one.
    }

    /**
     * Encodes UTF-8 text using URL-encoding
     */
    public static String encode(Object str) {
        if (str == null) {
            return "%NULL%";
        }
        return URLEncoder.encode(str.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Decodes URL-encoded UTF-8 text... except that we
     * first check if the string value is the token %NULL%,
     * which is our way to signify null.
     */
    public static String decode(String str) {
        mustNotBeNull(str);
        if (str.equals("%NULL%")) {
            return null;
        }
        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }


}


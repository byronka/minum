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
     * Returns text that has three symbols replaced -
     * the less-than, greater-than, and ampersand.
     * See https://www.w3.org/International/questions/qa-escapes#use
     * <br>
     * This will protect against something like <div>$USERNAME</div> allowing
     * a username of
     *      <script>alert(1)</script>
     * becoming
     *      <div><script>alert(1)</script</div>
     * and instead becomes
     *      <div>&lt;script&gt;alert(1)&lt;/script&gt;</div>
     *<br>
     * If the text is going inside an attribute (e.g. <div class="TEXT_GOES_HERE"> )
     * Then you need to escape slightly differently. In that case see [safeAttr]
     */
    public static String safeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    /**
     * Replace dangerous text that would go inside an HTML attribute.
     * See [safeHtml]
     * If we get a null string, just return an empty string
     */
    public static String safeAttr(String input) {
        if (input == null) {
            return "";
        }
        return input
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
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


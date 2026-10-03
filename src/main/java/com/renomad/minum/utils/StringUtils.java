package com.renomad.minum.utils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.renomad.minum.utils.Invariants.mustNotBeNull;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Some helper methods for Strings.
 */
public final class StringUtils {

    private StringUtils() {
        // making this private to be clearer it isn't supposed to be instantiated.
    }


    /**
     * Returns text that has three symbols replaced -
     * the less-than, greater-than, and ampersand.
     * See <a href="https://www.w3.org/International/questions/qa-escapes#use">...</a>
     * <br>
     * <pre>{@code
     * This will protect against something like <div>$USERNAME</div> allowing
     * a username of
     *      <script>alert(1)</script>
     * becoming
     *      <div><script>alert(1)</script</div>
     * and instead becomes
     *      <div>&lt;script&gt;alert(1)&lt;/script&gt;</div>
     * }</pre>
     * If the text is going inside an attribute (e.g. {@code <div class="TEXT_GOES_HERE">} )
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
     * See {@link #safeHtml(String)}
     * <br><br>
     * If we get a null string, just return an empty string
     * <br><br>
     * <p>
     *     Note: When cleaning data which will be used in an HTML
     *     attribute, ensure that the output of this method will
     *     be placed inside single or double quotes. <br><br>
     *
     *     Valid use: the key word HERE in the following: {@code <foo class="HERE">} or
     *     {@code <foo class='HERE'>}<br><br>
     *
     *     Invalid use: {@code <foo class=HERE>}
     * </p>
     * <pre>{@code
     * example:
     *   Given
     *      alert('XSS Attack')
     *   Get
     *      alert(&apos;XSS Attack&apos;)
     * }</pre>
     */
    public static String safeAttr(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    /**
     * Encodes UTF-8 text using URL-encoding
     */
    public static String encode(String str) {
        if (str == null) {
            return "%NULL%";
        }
        return URLEncoder.encode(str, UTF_8);
    }

    /**
     * Decodes URL-encoded UTF-8 text, except that we
     * first check if the string value is the token %NULL%,
     * which is our way to signify null.
     */
    public static String decode(String str) {
        mustNotBeNull(str);
        if (str.equals("%NULL%")) {
            return null;
        }
        return URLDecoder.decode(str, UTF_8);
    }

    /**
     * Returns a string of random characters.
     * @param length a positive non-zero number less than 1000
     */
    public static String generateSecureRandomString(int length) {
        int maximum = 1000;
        if (length <= 0) {
            throw new UtilsException("Input must be a positive non-zero number");
        }
        if (length > maximum) {
            throw new UtilsException("Input must be less than " + maximum);
        }
        final var allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        final var sr = new SecureRandom();

        return IntStream.range(1, length+1)
                .mapToObj (x -> allowedChars.charAt(sr.nextInt(allowedChars.length())))
                .map(Object::toString)
                .collect(Collectors.joining());
    }

    /**
     * Converts a list of bytes to a string. Returns null if the input is null.
     */
    public static String byteListToString(List<Byte> byteList) {
        if (byteList == null) return null;
        final int size = byteList.size();
        final var buf = new byte[size];
        for (int i = 0; i < size; i++) {
            buf[i] = byteList.get(i);
        }
        return new String(buf, UTF_8);
    }

    /**
     * Converts an array of bytes to a string. Returns null if the input is null.
     */
    public static String byteArrayToString(byte[] byteArray) {
        if (byteArray == null) return null;
        return new String(byteArray, UTF_8);
    }

    /**
     * A helper method to assess whether the input consists of only digits.
     * This helps us in the case of checking the content-length header, since
     * it must be pure numbers, no "-" or "+" symbols preceding it.
     * @return true if the given string consists exclusively of digits, like "1234",
     * false if anything else, like "+1234" or "12.34" or "" or null.
     */
    public static boolean isPureDigits(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }


}


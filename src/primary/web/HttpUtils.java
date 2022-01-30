package primary.web;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static utils.Invariants.requireNotNull;

public class HttpUtils {

    public static String readBody(Web.SocketWrapper sw, int length) {
        return sw.readByLength(length);
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
        requireNotNull(str);
        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }
}

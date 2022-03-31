package primary.web;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static utils.Invariants.mustNotBeNull;

public class HttpUtils {

    public static String readBody(Web.SocketWrapper sw, int length) {
        return sw.readByLength(length);
    }

}

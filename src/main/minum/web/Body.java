package minum.web;

import minum.Context;
import minum.utils.StringUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * This class represents the body of an HTML message.
 * See <a href="https://en.wikipedia.org/wiki/HTTP_message_body">Message Body on Wikipedia</a>
 *<br>
 * <pre>{@code
 * This could be a response from the web server:
 *
 * HTTP/1.1 200 OK
 * Date: Sun, 10 Oct 2010 23:26:07 GMT
 * Server: Apache/2.2.8 (Ubuntu) mod_ssl/2.2.8 OpenSSL/0.9.8g
 * Last-Modified: Sun, 26 Sep 2010 22:04:35 GMT
 * ETag: "45b6-834-49130cc1182c0"
 * Accept-Ranges: bytes
 * Content-Length: 12
 * Connection: close
 * Content-Type: text/html
 *
 * Hello world!
 * }</pre>
 * <p>
 *     The message body (or content) in this example is the text <pre>Hello world!</pre>.
 * </p>
 * @param bodyMap
 * @param raw
 */
public class Body {

    public static byte[] EMPTY_BYTES = new byte[0];
    private final Map<String, byte[]> bodyMap;
    private final byte[] raw;
    private final Context context;

    public static Body EMPTY(Context context) {
        return new Body(Map.of(), EMPTY_BYTES, context);
    }

    public Body(Map<String, byte[]> bodyMap, byte[] raw, Context context) {

        this.bodyMap = bodyMap;
        this.raw = raw;
        this.context = context;
    }

    public String asString(String key) {
        byte[] byteArray = bodyMap.get(key);
        if (byteArray == null) {
            return "";
        } else {
            return StringUtils.byteArrayToString(byteArray).trim();
        }

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Body body = (Body) o;
        return Objects.equals(bodyMap, body.bodyMap) && Arrays.equals(raw, body.raw);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(bodyMap);
        result = 31 * result + Arrays.hashCode(raw);
        return result;
    }

    public String asString() {
        return StringUtils.byteArrayToString(raw).trim();
    }

    public byte[] asBytes(String key) {
        return bodyMap.get(key);
    }

}

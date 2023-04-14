package atqa.web;

import atqa.utils.StringUtils;

import java.util.Map;

/**
 * An HTTP request
 */
public record Request(Headers headers, StartLine startLine, Body body) {

    static public class Body {

        private final Map<String, byte[]> bodyMap;

        public Body(Map<String, byte[]> bodyMap) {
            this.bodyMap = bodyMap;
        }

        public String asString(String key) {
            byte[] byteArray = bodyMap.get(key);
            if (byteArray == null) {
                return null;
            } else {
                return StringUtils.byteArrayToString(byteArray).trim();
            }

        }

        public byte[] asBytes(String key) {
            return bodyMap.get(key);
        }
    }

}

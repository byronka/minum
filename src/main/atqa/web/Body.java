package atqa.web;

import atqa.utils.StringUtils;

import java.util.Map;

public record Body(Map<String, byte[]> bodyMap, byte[] raw) {

    public static final Body EMPTY = new Body(Map.of(), new byte[0]);

    public String asString(String key) {
        byte[] byteArray = bodyMap.get(key);
        if (byteArray == null) {
            return "";
        } else {
            return StringUtils.byteArrayToString(byteArray).trim();
        }

    }

    public String asString() {
        return StringUtils.byteArrayToString(raw).trim();
    }

    public byte[] asBytes(String key) {
        return bodyMap.get(key);
    }
}

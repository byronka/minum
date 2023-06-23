package minum.utils;

import java.util.Map;

public record TagInfo(
        TagName tagName,
        Map<String, String> attributes
) {
    public static TagInfo EMPTY = new TagInfo(TagName.NULL, Map.of());
}
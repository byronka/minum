package com.renomad.minum.htmlparsing;

import java.util.Map;

/**
 * tagname and attributes inside an HTML5 tag
 */
public record TagInfo(
        TagName tagName,
        Map<String, String> attributes
) {
    /**
     * a null object
     */
    public static TagInfo EMPTY = new TagInfo(TagName.NULL, Map.of());
}
package com.renomad.minum.htmlparsing;

import java.util.Map;

/**
 * Represents a tag. It includes
 * everything inside the angle brackets - the
 * tagname, and the attributes.  for example,
 * <pre>
 *     {@code <p class="foo">}
 * </pre>
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
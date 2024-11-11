package com.renomad.minum.htmlparsing;

import java.util.*;

/**
 * tagname and attributes inside an HTML5 tag
 */
public final class TagInfo {

    private final TagName tagName;
    private final Map<String, String> attributes;

    public TagInfo(
            TagName tagName,
            Map<String, String> attributes
    ) {
        this.tagName = tagName;
        this.attributes = new HashMap<>(attributes);
    }

    /**
     * a null object
     */
    public static final TagInfo EMPTY = new TagInfo(TagName.NULL, Map.of());

    public TagName getTagName() {
        return tagName;
    }

    boolean containsAllAttributes(Set<Map.Entry<String, String>> entries) {
        return attributes.entrySet().containsAll(entries);
    }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    public Map<String, String> getAttributes() {
        return new HashMap<>(attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TagInfo tagInfo)) return false;
        return tagName == tagInfo.tagName && Objects.equals(attributes, tagInfo.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagName, attributes);
    }

    @Override
    public String toString() {
        return "TagInfo{" +
                "tagName=" + tagName +
                ", attributes=" + attributes +
                '}';
    }

}
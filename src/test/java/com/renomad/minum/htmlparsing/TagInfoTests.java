package com.renomad.minum.htmlparsing;

import org.junit.Test;

import java.util.Map;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class TagInfoTests {

    @Test
    public void happyPath() {
        TagInfo tagInfo = new TagInfo(TagName.P, Map.of("class", "foo"));
        assertEquals(tagInfo.toString(), "TagInfo{tagName=P, attributes={class=foo}}");
    }
}

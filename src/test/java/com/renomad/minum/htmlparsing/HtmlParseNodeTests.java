package com.renomad.minum.htmlparsing;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class HtmlParseNodeTests {

    @Test
    public void testInnerText_EdgeCases() {
        assertEquals(HtmlParseNode.innerText(List.of()), "");
        assertEquals(HtmlParseNode.innerText(List.of(HtmlParseNode.EMPTY, HtmlParseNode.EMPTY)), "");
        assertEquals(HtmlParseNode.innerText(List.of(new HtmlParseNode(ParseNodeType.ELEMENT, TagInfo.EMPTY, List.of(), ""))), "");
    }

    @Test
    public void test_recursiveTreeWalk() {
        ArrayList<String> myList = new ArrayList<>();
        List<HtmlParseNode> innerContent = new ArrayList<>();
        HtmlParseNode.recursiveTreeWalk(myList, innerContent, null);
        assertEquals(myList.size(), 0);
    }
}

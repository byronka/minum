package com.renomad.minum.htmlparsing;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.SearchHelpers.innerText;
import static com.renomad.minum.htmlparsing.HtmlParseNode.recursiveTreeWalk;
import static com.renomad.minum.testing.TestFramework.assertEquals;

public class HtmlParseNodeTests {

    @Test
    public void testInnerText_EdgeCases() {
        assertEquals(innerText(List.of()), "");
        assertEquals(innerText((List<HtmlParseNode>) null), "");
        assertEquals(innerText(List.of(HtmlParseNode.EMPTY, HtmlParseNode.EMPTY)), "[EMPTY HTMLPARSENODE][EMPTY HTMLPARSENODE]");
        assertEquals(innerText(List.of(new HtmlParseNode(ParseNodeType.ELEMENT, TagInfo.EMPTY, List.of(), ""))), "[]");
    }

    @Test
    public void testInnerText_HappyPath() {
        HtmlParseNode innerNode = new HtmlParseNode(ParseNodeType.CHARACTERS, TagInfo.EMPTY, List.of(), "This is the text");
        String innerText = innerText(List.of(innerNode));
        assertEquals(innerText, "This is the text");
    }

    @Test
    public void testHappyPath() {
        HtmlParseNode node1 = new HtmlParseNode(ParseNodeType.CHARACTERS, TagInfo.EMPTY, List.of(), "This is the text");
        HtmlParseNode node2 = new HtmlParseNode(ParseNodeType.ELEMENT, new TagInfo(TagName.P, Map.of()), List.of(node1), "");

        assertEquals(node1.getType(), ParseNodeType.CHARACTERS);
        assertEquals(node1.getInnerContent(), List.of());
        assertEquals(node1.getTextContent(), "This is the text");

        assertEquals(node2.getType(), ParseNodeType.ELEMENT);
        assertEquals(node2.getInnerContent(), List.of(node1));
        assertEquals(node2.getTextContent(), "");
    }

    @Test
    public void test_recursiveTreeWalk() {
        ArrayList<String> myList = new ArrayList<>();
        List<HtmlParseNode> innerContent = new ArrayList<>();
        recursiveTreeWalk(myList, innerContent, null);
        assertEquals(myList.size(), 0);
    }



    @Test
    public void testInnerText_EdgeCases_DEPRECATED() {
        assertEquals(HtmlParseNode.innerText(List.of()), "");
        assertEquals(HtmlParseNode.innerText(null), "");
        assertEquals(HtmlParseNode.innerText(List.of(HtmlParseNode.EMPTY, HtmlParseNode.EMPTY)), "[EMPTY HTMLPARSENODE][EMPTY HTMLPARSENODE]");
        assertEquals(HtmlParseNode.innerText(List.of(new HtmlParseNode(ParseNodeType.ELEMENT, TagInfo.EMPTY, List.of(), ""))), "[]");
    }

    @Test
    public void testInnerText_HappyPath_DEPRECATED() {
        HtmlParseNode innerNode = new HtmlParseNode(ParseNodeType.CHARACTERS, TagInfo.EMPTY, List.of(), "This is the text");
        String innerText = HtmlParseNode.innerText(List.of(innerNode));
        assertEquals(innerText, "This is the text");
    }

    @Test
    public void test_recursiveTreeWalk_DEPRECATED() {
        ArrayList<String> myList = new ArrayList<>();
        List<HtmlParseNode> innerContent = new ArrayList<>();
        recursiveTreeWalk(myList, innerContent, null);
        assertEquals(myList.size(), 0);
    }
}

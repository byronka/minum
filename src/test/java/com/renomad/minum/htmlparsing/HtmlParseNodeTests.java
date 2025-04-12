package com.renomad.minum.htmlparsing;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.SearchHelpers.innerText;
import static com.renomad.minum.htmlparsing.HtmlParseNode.recursiveTreeWalk;
import static com.renomad.minum.testing.TestFramework.assertEquals;

public class HtmlParseNodeTests {

    /**
     * Examine some edge cases for the input, such as
     * an empty string, a null, etc.
     */
    @Test
    public void testInnerText_EdgeCases() {
        assertEquals(innerText(List.of()), "");
        assertEquals(innerText((List<HtmlParseNode>) null), "");
        assertEquals(innerText(List.of(HtmlParseNode.EMPTY, HtmlParseNode.EMPTY)), "[EMPTY HTMLPARSENODE][EMPTY HTMLPARSENODE]");
        assertEquals(innerText(List.of(new HtmlParseNode(ParseNodeType.ELEMENT, TagInfo.EMPTY, List.of(), ""))), "[]");
    }

    /**
     * This is an example of a typical use case of the innerText method
     */
    @Test
    public void testInnerText_HappyPath() {
        HtmlParseNode innerNode = new HtmlParseNode(ParseNodeType.CHARACTERS, TagInfo.EMPTY, List.of(), "This is the text");
        String innerText = innerText(List.of(innerNode));
        assertEquals(innerText, "This is the text");
    }

    /**
     * Examine some typical expected behavior of constructing an
     * ordinary {@link HtmlParseNode}
     */
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

    /**
     * What should happen if we provide empty data to recursiveTreeWalk
     */
    @Test
    public void test_recursiveTreeWalk_EdgeCase_Empty() {
        ArrayList<String> myList = new ArrayList<>();
        List<HtmlParseNode> innerContent = new ArrayList<>();
        recursiveTreeWalk(myList, innerContent, null);
        assertEquals(myList.size(), 0);
    }

    /**
     * This test is similar to {@link #testInnerText_EdgeCases} except that
     * it is using {@link HtmlParseNode#innerText(List)} to collect data. This
     * is not the recommended approach for users, since it relies on methods
     * that are not publicly scoped for general outside use.
     * <br>
     * Instead, users are recommended to use a program similar to {@link com.renomad.minum.SearchHelpers#innerText(List)},
     * which use the publicly-scoped methods and achieve an identical result.
     * @see #testInnerText_EdgeCases
     */
    @Test
    public void testInnerText_EdgeCases_DEPRECATED() {
        assertEquals(HtmlParseNode.innerText(List.of()), "");
        assertEquals(HtmlParseNode.innerText(null), "");
        assertEquals(HtmlParseNode.innerText(List.of(HtmlParseNode.EMPTY, HtmlParseNode.EMPTY)), "[EMPTY HTMLPARSENODE][EMPTY HTMLPARSENODE]");
        assertEquals(HtmlParseNode.innerText(List.of(new HtmlParseNode(ParseNodeType.ELEMENT, TagInfo.EMPTY, List.of(), ""))), "[]");
    }

    /**
     * This test is similar to {@link #testInnerText_HappyPath} except that
     * it is using {@link HtmlParseNode#innerText(List)} to collect data. This
     * is not the recommended approach for users, since it relies on methods
     * that are not publicly scoped for general outside use.
     * <br>
     * Instead, users are recommended to use a program similar to {@link com.renomad.minum.SearchHelpers#innerText(List)},
     * which use the publicly-scoped methods and achieve an identical result.
     * @see #testInnerText_HappyPath()
     */
    @Test
    public void testInnerText_HappyPath_DEPRECATED() {
        HtmlParseNode innerNode = new HtmlParseNode(ParseNodeType.CHARACTERS, TagInfo.EMPTY, List.of(), "This is the text");
        String innerText = HtmlParseNode.innerText(List.of(innerNode));
        assertEquals(innerText, "This is the text");
    }

    @Test
    public void test_ToString() {
        HtmlParser htmlParser = new HtmlParser();
        List<HtmlParseNode> parsed = htmlParser.parse("<a href=\"foo\">Tester</a>");
        HtmlParseNode htmlParseNode = parsed.getFirst();
        assertEquals(htmlParseNode.toString(), "<a href=\"foo\">Tester</a>");
    }

    @Test
    public void test_ToString_NestedContent() {
        HtmlParser htmlParser = new HtmlParser();
        List<HtmlParseNode> parsed = htmlParser.parse("<a href=\"foo\"><p>Tester</p></a>");
        HtmlParseNode htmlParseNode = parsed.getFirst();
        assertEquals(htmlParseNode.toString(), "<a href=\"foo\"><p>Tester</p></a>");
    }

    @Test
    public void test_ToString_DeeperNestedContent() {
        HtmlParser htmlParser = new HtmlParser();
        List<HtmlParseNode> parsed = htmlParser.parse("<a href=\"foo\"><p><div>Tester</div></p></a>");
        HtmlParseNode htmlParseNode = parsed.getFirst();
        assertEquals(htmlParseNode.toString(), "<a href=\"foo\"><p><div>Tester</div></p></a>");
    }

    @Test
    public void test_ToString_TextAdjacentToElement() {
        HtmlParser htmlParser = new HtmlParser();
        List<HtmlParseNode> parsed = htmlParser.parse("<a href=\"foo\">Tester and <p>another tester</p></a>");
        HtmlParseNode htmlParseNode = parsed.getFirst();
        assertEquals(htmlParseNode.toString(), "<a href=\"foo\">Tester and <p>another tester</p></a>");
    }

    @Test
    public void test_ToString_MultipleAdjacentElements() {
        HtmlParser htmlParser = new HtmlParser();
        List<HtmlParseNode> parsed = htmlParser.parse("<a href=\"foo\"><p class=\"abc1\">Test1</p><p class=\"abc2\">Test2</p><p class=\"abc3\">Test3</p></a>");
        HtmlParseNode htmlParseNode = parsed.getFirst();
        assertEquals(htmlParseNode.toString(), "<a href=\"foo\"><p class=\"abc1\">Test1</p><p class=\"abc2\">Test2</p><p class=\"abc3\">Test3</p></a>");
    }
}

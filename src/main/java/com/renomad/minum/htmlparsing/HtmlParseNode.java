package com.renomad.minum.htmlparsing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the expected types of things we may encounter when parsing an HTML string, which
 * for our purposes is {@link ParseNodeType}.
 * <p>
 * See <a href="https://www.w3.org/TR/2011/WD-html-markup-20110113/syntax.html#syntax-elements">W3.org Elements</a>
 * </p>
 */
public final class HtmlParseNode {

    private final ParseNodeType type;
    private final TagInfo tagInfo;
    private final List<HtmlParseNode> innerContent;
    private final String textContent;

    public HtmlParseNode(ParseNodeType type,
                         TagInfo tagInfo,
                         List<HtmlParseNode> innerContent,
                         String textContent) {

        this.type = type;
        this.tagInfo = tagInfo;
        this.innerContent = new ArrayList<>(innerContent);
        this.textContent = textContent;
    }

    public static final HtmlParseNode EMPTY = new HtmlParseNode(ParseNodeType.ELEMENT, TagInfo.EMPTY, List.of(), "EMPTY HTMLPARSENODE");

    /**
     * Return a list of strings of the text content of the tree.
     * <p>
     * This method traverses the tree from this node downwards,
     * adding the text content as it goes. Its main purpose is to
     * quickly render all the strings out of an HTML document at once.
     * </p>
     */
    public List<String> print() {
        var myList = new ArrayList<String>();
        recursiveTreeWalk(myList, innerContent, textContent);
        return myList;
    }

    static void recursiveTreeWalk(List<String> myList, List<HtmlParseNode> innerContent, String textContent) {
        for (HtmlParseNode hpn : innerContent) {
            recursiveTreeWalk(myList, hpn.innerContent, hpn.textContent);
        }
        if (textContent != null && ! textContent.isBlank()) {
            myList.add(textContent);
        }
    }

    /**
     * Return a list of {@link HtmlParseNode} nodes in the HTML that match provided attributes.
     */
    public List<HtmlParseNode> search(TagName tagName, Map<String, String> attributes) {
        var myList = new ArrayList<HtmlParseNode>();
        recursiveTreeWalkSearch(myList, tagName, attributes);
        return myList;
    }

    private void recursiveTreeWalkSearch(List<HtmlParseNode> myList, TagName tagName, Map<String, String> attributes) {
        if (this.tagInfo.getTagName().equals(tagName) && this.tagInfo.containsAllAttributes(attributes.entrySet())) {
            myList.add(this);
        }
        for (var htmlParseNode : innerContent) {
            htmlParseNode.recursiveTreeWalkSearch(myList, tagName, attributes);
        }
    }

    /**
     * Return the inner text of these nodes
     * <p>
     *      If this element has only one inner
     *      content item, and it's a {@link ParseNodeType#CHARACTERS} element, return its text content.
     * </p>
     * <p>
     *     If there is more than one node, run the {@link #print()} command on each, appending
     *     to a single string.
     * </p>
     */
    static String innerText(List<HtmlParseNode> innerContent) {
        if (innerContent == null) return "";
        if (innerContent.size() == 1 && innerContent.getFirst().type == ParseNodeType.CHARACTERS) {
            return innerContent.getFirst().textContent;
        } else {
            StringBuilder sb = new StringBuilder();
            for (HtmlParseNode hpn : innerContent) {
                sb.append(hpn.print());
            }
            return sb.toString();
        }
    }

    public ParseNodeType getType() {
        return type;
    }

    public TagInfo getTagInfo() {
        return tagInfo;
    }

    public List<HtmlParseNode> getInnerContent() {
        return new ArrayList<>(innerContent);
    }

    void addToInnerContent(HtmlParseNode htmlParseNode) {
        innerContent.add(htmlParseNode);
    }

    public String getTextContent() {
        return textContent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HtmlParseNode that)) return false;
        return type == that.type && Objects.equals(tagInfo, that.tagInfo) && Objects.equals(innerContent, that.innerContent) && Objects.equals(textContent, that.textContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, tagInfo, innerContent, textContent);
    }

    public String innerText() {
        return innerText(innerContent);
    }

}
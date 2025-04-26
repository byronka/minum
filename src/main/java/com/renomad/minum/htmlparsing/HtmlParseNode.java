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

    /**
     * Gets the type of this node - either it's an element, with opening and
     * closing tags and attributes and an inner content, or it's just plain text.
     */
    public ParseNodeType getType() {
        return type;
    }

    /**
     * Returns the {@link TagInfo}, which contains valuable information
     * like the type of element (p, a, div, and so on) and attributes
     * like class, id, etc.
     */
    public TagInfo getTagInfo() {
        return tagInfo;
    }

    /**
     * The inner content is the data between the opening and closing
     * tags of this element, comprised of potentially other complex
     * elements and/or characters or a mix (or nothing at all, which
     * will return an empty list).
     */
    public List<HtmlParseNode> getInnerContent() {
        return new ArrayList<>(innerContent);
    }

    void addToInnerContent(HtmlParseNode htmlParseNode) {
        innerContent.add(htmlParseNode);
    }

    /**
     * If the {@link ParseNodeType} is {@link ParseNodeType#CHARACTERS}, then this
     * will have text content.  Otherwise, it returns an empty string.
     */
    public String getTextContent() {
        return textContent;
    }

    /**
     * Return the inner text of a node
     * <p>
     *      If this element has only one inner
     *      content item, and it's a {@link ParseNodeType#CHARACTERS} element, return its text content.
     * </p>
     * <p>
     *     If there is more than one node, concatenates them to a single string, with each section wrapped
     *     in square brackets.
     * </p>
     */
    public String innerText() {
        return innerText(innerContent);
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

    @Override
    public String toString() {
        if (this.getType().equals(ParseNodeType.ELEMENT)) {
            var sb = new StringBuilder();
            sb.append("<");
            String lowercaseElement = this.tagInfo.getTagName().toString().toLowerCase();
            sb.append(lowercaseElement);

            for (Map.Entry<String, String> entry : this.tagInfo.getAttributes().entrySet()) {
                sb.append(" ");
                sb.append(entry.getKey());
                sb.append("=");
                sb.append("\"").append(entry.getValue()).append("\"");
            }

            sb.append(">");

            for (HtmlParseNode hpn : this.innerContent) {
                sb.append(hpn);
            }

            sb.append("</").append(lowercaseElement).append(">");
            return sb.toString();
        } else {
            return textContent;
        }


    }

}
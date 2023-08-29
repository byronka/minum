package com.renomad.minum.htmlparsing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.utils.Invariants.mustBeTrue;

/**
 * These nodes represent the types of things we may encounter when parsing an HTML string, which
 * for our purposes is just the types in {@link ParseNodeType}.  Depending on the type,
 * different values will be filled out, but by coalescing them into this one class, it simplifies
 * our code.
 * See <a href="https://www.w3.org/TR/2011/WD-html-markup-20110113/syntax.html#syntax-elements">W3.org Elements</a>
 */
public record HtmlParseNode(ParseNodeType type,
                            TagInfo tagInfo,
                            List<HtmlParseNode> innerContent,
                            String textContent) {

    public static final HtmlParseNode EMPTY = new HtmlParseNode(ParseNodeType.ELEMENT, TagInfo.EMPTY, List.of(), "EMPTY HTMLPARSENODE");

    /**
     * This method traverses the tree from this node downwards,
     * adding the text content as it goes.
     */
    public List<String> print() {
        var myList = new ArrayList<String>();
        recursiveTreeWalk(myList);
        return myList;
    }

    private void recursiveTreeWalk(ArrayList<String> myList) {
        for (var hpn : innerContent) {
            hpn.recursiveTreeWalk(myList);
        }
        if (textContent != null && ! textContent.isBlank()) {
            myList.add(textContent);
        }
    }

    /**
     * This searches for a single node in the HTML.  If it
     * finds more than one match, it will throw an exception.
     * If none are found, HtmlParseNode.EMPTY is returned.
     */
    public List<HtmlParseNode> search(TagName tagName, Map<String, String> attributes) {
        var myList = new ArrayList<HtmlParseNode>();
        recursiveTreeWalkSearch(myList, tagName, attributes);
        return myList;
    }

    private void recursiveTreeWalkSearch(ArrayList<HtmlParseNode> myList, TagName tagName, Map<String, String> attributes) {
        if (this.tagInfo().tagName().equals(tagName) && this.tagInfo().attributes().entrySet().containsAll(attributes.entrySet())) {
            myList.add(this);
        }
        for (var htmlParseNode : innerContent) {
            htmlParseNode.recursiveTreeWalkSearch(myList, tagName, attributes);
        }
    }

    /**
     * If the element you're looking at has just one inner
     * content item, and it's a CHARACTERS element, return it.
     */
    public String innerText() {
       if (innerContent.size() == 1 && innerContent.get(0).type == ParseNodeType.CHARACTERS) {
           return innerContent.get(0).textContent;
       } else {
           return "";
       }
    }

}
package com.renomad.minum;

import com.renomad.minum.htmlparsing.HtmlParseNode;
import com.renomad.minum.htmlparsing.HtmlParser;
import com.renomad.minum.htmlparsing.ParseNodeType;
import com.renomad.minum.htmlparsing.TagName;
import com.renomad.minum.utils.InvariantException;
import com.renomad.minum.web.Body;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class makes use of the HtmlParser's methods to enable
 * searching and printing of the values.
 * <br>
 * It restricts itself to using the public methods, so these
 * methods may server as examples for future capability.
 */
public class SearchHelpers {


    /**
     * Presuming the response body is HTML, search for a single
     * HTML element with the given tag name and attributes.
     *
     * @return {@link HtmlParseNode#EMPTY} if none found, a particular node if found,
     * and an exception thrown if more than one found.
     */
    public static HtmlParseNode searchOne(Body body, TagName tagName, Map<String, String> attributes) {
        var htmlParser = new HtmlParser();
        List<HtmlParseNode> nodes = htmlParser.parse(body.asString());
        var searchResults = search(nodes, tagName, attributes);
        if (searchResults.size() > 1) {
            throw new InvariantException("More than 1 node found.  Here they are:" + searchResults);
        }
        if (searchResults.isEmpty()) {
            return HtmlParseNode.EMPTY;
        } else {
            return searchResults.getFirst();
        }
    }


    /**
     * Search the node tree for elements that match a set of attributes
     * <p>
     * If zero nodes are found, returns an empty list.
     * </p>
     */
    public static List<HtmlParseNode> search(Body body, TagName tagName, Map<String, String> attributes) {
        var htmlParser = new HtmlParser();
        List<HtmlParseNode> nodes = htmlParser.parse(body.asString());
        return search(nodes, tagName, attributes);
    }

    /**
     * Presuming the response body is HTML, search for a single
     * HTML element with the given tag name and attributes.
     *
     * @return {@link HtmlParseNode#EMPTY} if none found, a particular node if found,
     * and an exception thrown if more than one found.
     */
    public static HtmlParseNode searchOne(Body body, Map<String, String> attributes) {
        var htmlParser = new HtmlParser();
        List<HtmlParseNode> nodes = htmlParser.parse(body.asString());
        var searchResults = search(nodes, attributes);
        if (searchResults.size() > 1) {
            throw new InvariantException("More than 1 node found.  Here they are:" + searchResults);
        }
        if (searchResults.isEmpty()) {
            return HtmlParseNode.EMPTY;
        } else {
            return searchResults.getFirst();
        }
    }

    /**
     * Search the node tree for elements that match a set of attributes
     * <p>
     * If zero nodes are found, returns an empty list.
     * </p>
     */
    public static List<HtmlParseNode> search(Body body, Map<String, String> attributes) {
        var htmlParser = new HtmlParser();
        List<HtmlParseNode> nodes = htmlParser.parse(body.asString());
        return search(nodes, attributes);
    }


    /**
     * Presuming the response body is HTML, search for a single
     * HTML element with the given tag name and attributes.
     *
     * @return {@link HtmlParseNode#EMPTY} if none found, a particular node if found,
     * and an exception thrown if more than one found.
     */
    public static HtmlParseNode searchOne(Body body, TagName tagName) {
        var htmlParser = new HtmlParser();
        List<HtmlParseNode> nodes = htmlParser.parse(body.asString());
        var searchResults = search(nodes, tagName);
        if (searchResults.size() > 1) {
            throw new InvariantException("More than 1 node found.  Here they are:" + searchResults);
        }
        if (searchResults.isEmpty()) {
            return HtmlParseNode.EMPTY;
        } else {
            return searchResults.getFirst();
        }
    }

    /**
     * Search the node tree for elements that match a tagname
     * <p>
     * If zero nodes are found, returns an empty list.
     * </p>
     */
    public static List<HtmlParseNode> search(Body body, TagName tagName) {
        var htmlParser = new HtmlParser();
        var nodes = htmlParser.parse(body.asString());
        return search(nodes, tagName);
    }


    /**
     * Search the node tree for elements that match a set of attributes
     * <p>
     * If zero nodes are found, returns an empty list.
     * </p>
     */
    public static List<HtmlParseNode> search(List<HtmlParseNode> nodes, TagName tagName, Map<String, String> attributes) {
        List<HtmlParseNode> foundNodes = new ArrayList<>();
        for (var node : nodes) {
            var result = search(node, tagName, attributes);
            foundNodes.addAll(result);
        }
        return foundNodes;
    }

    /**
     * Return a list of {@link HtmlParseNode} nodes in the HTML that match provided attributes.
     */
    private static List<HtmlParseNode> search(HtmlParseNode node, TagName tagName, Map<String, String> attributes) {
        var myList = new ArrayList<HtmlParseNode>();
        recursiveTreeWalkSearch(node, myList, tagName, attributes);
        return myList;
    }

    private static void recursiveTreeWalkSearch(HtmlParseNode node, List<HtmlParseNode> myList, TagName tagName, Map<String, String> attributes) {
        if (node.getTagInfo().getTagName().equals(tagName) && node.getTagInfo().getAttributes().entrySet().containsAll(attributes.entrySet())) {
            myList.add(node);
        }
        for (var htmlParseNode : node.getInnerContent()) {
            recursiveTreeWalkSearch(htmlParseNode, myList, attributes);
        }
    }

    /**
     * Search the node tree for elements that match a set of attributes
     * <p>
     * If zero nodes are found, returns an empty list.
     * </p>
     */
    public static List<HtmlParseNode> search(List<HtmlParseNode> nodes, Map<String, String> attributes) {
        List<HtmlParseNode> foundNodes = new ArrayList<>();
        for (var node : nodes) {
            var result = search(node, attributes);
            foundNodes.addAll(result);
        }
        return foundNodes;
    }

    /**
     * Return a list of {@link HtmlParseNode} nodes in the HTML that match provided attributes.
     */
    private static List<HtmlParseNode> search(HtmlParseNode node, Map<String, String> attributes) {
        var myList = new ArrayList<HtmlParseNode>();
        recursiveTreeWalkSearch(node, myList, attributes);
        return myList;
    }

    private static void recursiveTreeWalkSearch(HtmlParseNode node, List<HtmlParseNode> myList, Map<String, String> attributes) {
        if (node.getTagInfo().getAttributes().entrySet().containsAll(attributes.entrySet())) {
            myList.add(node);
        }
        for (var htmlParseNode : node.getInnerContent()) {
            recursiveTreeWalkSearch(htmlParseNode, myList, attributes);
        }
    }


    /**
     * Search the node tree for elements that match a tagname
     * <p>
     * If zero nodes are found, returns an empty list.
     * </p>
     */
    public static List<HtmlParseNode> search(List<HtmlParseNode> nodes, TagName tagName) {
        List<HtmlParseNode> foundNodes = new ArrayList<>();
        for (var node : nodes) {
            var result = search(node, tagName);
            foundNodes.addAll(result);
        }
        return foundNodes;
    }


    /**
     * Return a list of {@link HtmlParseNode} nodes in the HTML that match a provided tagname.
     */
    private static List<HtmlParseNode> search(HtmlParseNode node, TagName tagName) {
        var myList = new ArrayList<HtmlParseNode>();
        recursiveTreeWalkSearch(node, myList, tagName);
        return myList;
    }

    private static void recursiveTreeWalkSearch(HtmlParseNode node, List<HtmlParseNode> myList, TagName tagName) {
        if (node.getTagInfo().getTagName().equals(tagName)) {
            myList.add(node);
        }
        for (var htmlParseNode : node.getInnerContent()) {
            recursiveTreeWalkSearch(htmlParseNode, myList, tagName);
        }
    }


    /**
     * Return the inner text of these nodes
     * <p>
     *      If this element has only one inner
     *      content item, and it's a {@link ParseNodeType#CHARACTERS} element, return its text content.
     * </p>
     * <p>
     *     If there is more than one node, run the {@link #print(HtmlParseNode)} command on each, appending
     *     to a single string.
     * </p>
     */
    public static String innerText(List<HtmlParseNode> innerContent) {
        if (innerContent == null) return "";
        if (innerContent.size() == 1 && innerContent.getFirst().getType() == ParseNodeType.CHARACTERS) {
            return innerContent.getFirst().getTextContent();
        } else {
            StringBuilder sb = new StringBuilder();
            for (HtmlParseNode hpn : innerContent) {
                sb.append(print(hpn));
            }
            return sb.toString();
        }
    }

    /**
     * Return a list of strings of the text content of the tree.
     * <p>
     * This method traverses the tree from this node downwards,
     * adding the text content as it goes. Its main purpose is to
     * quickly render all the strings out of an HTML document at once.
     * </p>
     */
    public static List<String> print(HtmlParseNode hpn) {
        var myList = new ArrayList<String>();
        recursiveTreeWalk(myList, hpn.getInnerContent(), hpn.getTextContent());
        return myList;
    }

    static void recursiveTreeWalk(List<String> myList, List<HtmlParseNode> innerContent, String textContent) {
        for (HtmlParseNode hpn : innerContent) {
            recursiveTreeWalk(myList, hpn.getInnerContent(), hpn.getTextContent());
        }
        if (textContent != null && ! textContent.isBlank()) {
            myList.add(textContent);
        }
    }

    /**
     * Return a single string containing the text of this node
     */
    public static String innerText(HtmlParseNode hpn) {
        return innerText(hpn.getInnerContent());
    }
}

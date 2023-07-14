package minum.htmlparsing;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * This method traverses the tree from this node downwards,
     * adding the text content as it goes. We follow an in-order
     * algorithm for walking the tree, like this pseudocode,
     * found at Wikipedia:
     * <h3>
     *     In-order, LNR
     * </h3>
     * <ol>
     * <li>Recursively traverse the current node's left subtree.</li>
     * <li>Visit the current node.</li>
     * <li>Recursively traverse the current node's right subtree.</li>
     * </ol>
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
}
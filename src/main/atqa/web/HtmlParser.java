package atqa.web;

import atqa.exceptions.ForbiddenUseException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class HtmlParser {

    /**
     * Most total chars we'll read.
     */
    final static int MAX_HTML_SIZE = 2 * 1024 * 1024;

    /**
     * largest element name size we will allow.
     */
    final static int MAX_ELEMENT_NAME_SIZE = 50;

    /**
     * When reading the inner text content of an HTML
     * element, this is the most we'll read.
     */
    final static int MAX_CHARACTER_CONTENT_SIZE = 1024;

    /**
     * The different kinds of things in an HTML document.
     */
    public enum ParseNodeType {
        ELEMENT,
        CHARACTERS
    }

    /**
     * These nodes represent the types of things we may encounter when parsing an HTML string, which
     * for our purposes is just the types in {@link ParseNodeType}.  Depending on the type,
     * different values will be filled out, but by coalescing them into this one class, it simplifies
     * our code.
     * See <a href="https://www.w3.org/TR/2011/WD-html-markup-20110113/syntax.html#syntax-elements">W3.org Elements</a>
     */
    public record HtmlParseNode(ParseNodeType type,
                                String elementName,
                                List<HtmlParseNode> innerContent,
                                String textContent) { }

    /**
     * Given any HTML input, scan through and generate a tree
     * of HTML nodes.  Return the root of the tree.
     * <p>
     *     This parser operates with a very particular paradigm in mind. I'll explain
     *     it through examples.  Let's look at some typical HTML:
     * </p>
     * <pre>{@code <p>Hello world</p>}</pre>
     * <p>
     *     The way we will model this is as follows:
     * </p>
     * <pre>{@code <ELEMENT_NAME_AND_DETAILS>content<END_OF_ELEMENT>}</pre>
     * <p>
     *     Basically, we'll examine the first part, "ELEMENT_NAME_AND_DETAILS", and
     *     grab the element's name and any attributes.  Then we'll descend into the
     *     content section.  We'll know we've hit the end of the element by keeping
     *     track of how far we've descended/ascended and whether we are hitting
     *     a closing HTML element.
     * </p>
     * <p>
     *     Complicating this is that elements may not have content, for example
     *     any <a href="https://www.w3.org/TR/2011/WD-html-markup-20110113/syntax.html#void-element_xref3">void elements</a>
     *     or when a user chooses to create an empty tag
     * </p>
     */
    public static List<HtmlParseNode> parse(String input) throws IOException {
        if (input.length() > MAX_HTML_SIZE)
            throw new ForbiddenUseException("We will not parse a string larger than MAX_HTML_SIZE: " + MAX_HTML_SIZE + " characters");
        var sr = new StringReader(input);
        return innerParser(sr);
    }

    public static List<HtmlParseNode> innerParser(StringReader sr) throws IOException {
        List<HtmlParseNode> nodes = new ArrayList<>();
        for(int i = 0; i < MAX_HTML_SIZE; i++) {
            if (i == MAX_HTML_SIZE - 1) throw new ForbiddenUseException("Ceasing to parse HTML - too large");
            int currentChar = sr.read();
            if (currentChar < 0) break;
            if ((char) currentChar == '<') {
                String elementDetails = getElementNameAndDetails(sr);
                List<HtmlParseNode> innerContent = innerParser(sr);
                nodes.add(new HtmlParseNode(ParseNodeType.ELEMENT, elementDetails, innerContent, null));
            } else {
                // in this case, it's just text inside.
                String textContent = getTextContent(sr);
                nodes.add(new HtmlParseNode(ParseNodeType.CHARACTERS, null, null, textContent));
            }
        }
        return nodes;
    }

    private static String getTextContent(StringReader sr) throws IOException {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < MAX_CHARACTER_CONTENT_SIZE; i++) {
            if (i == MAX_CHARACTER_CONTENT_SIZE - 1) throw new ForbiddenUseException("Ceasing to parse HTML inner text content - too large");
            int currentChar = sr.read();
            if (currentChar < 0) break;
            if ((char) currentChar == '<')  {
                break;
            } else {
                sb.append((char) currentChar);
            }
        }
        return sb.toString();
    }

    /**
     * Grab the details
     */
    public static String getElementNameAndDetails(StringReader sr) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_ELEMENT_NAME_SIZE; i++) {
            if (i == MAX_ELEMENT_NAME_SIZE - 1) throw new ForbiddenUseException("Ceasing to parse HTML element name - too large");
            int currentChar = sr.read();
            if (currentChar < 0) break;
            if ((char) currentChar == ' ' || (char) currentChar == '>')  {
                break;
            } else {
                sb.append((char) currentChar);
            }
        }
        return sb.toString();
    }
}

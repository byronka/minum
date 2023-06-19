package atqa.web;

import atqa.exceptions.ForbiddenUseException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
     * Possible tag names per the W3C HTML spec.
     */
    public enum TagName {
        // first we'll list the void elements
        AREA(true), BASE(true), BR(true), COL(true), COMMAND(true),
        EMBED(true), HR(true), IMG(true), INPUT(true), KEYGEN(true),
        LINK(true), META(true), PARAM(true), SOURCE(true),
        TRACK(true), WBR(true),

        HTML(false), HEAD(false), TITLE(false), STYLE(false),
        SCRIPT(false), NOSCRIPT(false), BODY(false), SECTION(false),
        NAV(false), ARTICLE(false), ASIDE(false), H1(false),
        H2(false), H3(false), H4(false), H5(false), H6(false),
        HGROUP(false), HEADER(false), FOOTER(false), ADDRESS(false),
        P(false), PRE(false), BLOCKQUOTE(false), OL(false), UL(false),
        LI(false), DL(false), DT(false), DD(false), FIGURE(false),
        FIG(false), CAPTION(false), DIV(false), A(false), EM(false),
        STRONG(false), SMALL(false), CITE(false), Q(false),
        DFN(false), ABBR(false), TIME(false), CODE(false), VAR(false),
        SAMP(false), KBD(false), SUB(false), SUP(false), I(false),
        B(false),

        /**
         * Used to indicate that no tag was found, to avoid use of null
         */
        NULL(false)
        ;

        private final boolean isVoidElement;

        /**
         * If this is a void element, then it is disallowed to have
         * a closing tag.  (see <a href="https://www.w3.org/TR/2011/WD-html-markup-20110113/syntax.html#void-element">void elements</a>)
         * @param isVoidElement
         */
        TagName(boolean isVoidElement) {
            this.isVoidElement = isVoidElement;
        }
    }

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
                                String textContent) { }

    public record TagInfo(
            TagName tagName,
            Map<String, String> attributes,
            /*
             * If this tag is a closing tag, then we
             * are at the end of an HTML element
             */
            boolean isClosingTag
            ) {
        public static TagInfo EMPTY = new TagInfo(TagName.NULL, Map.of(), false);
    }
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
        return innerParser(sr, 0);
    }

    public static List<HtmlParseNode> innerParser(StringReader sr, int depth) throws IOException {
        List<HtmlParseNode> nodes = new ArrayList<>();
        int currentChar = sr.read();
        if (currentChar < 0) return List.of();
        if ((char) currentChar == '<') {
            // See https://www.w3.org/TR/2011/WD-html-markup-20110113/syntax.html#syntax-start-tags
            TagInfo sti = getTagInformation(sr);
            if (sti.isClosingTag) return List.of();
            List<HtmlParseNode> innerContent = new ArrayList<>();
            if (! sti.tagName.isVoidElement) {
                innerContent = innerParser(sr, depth + 1);
            }
            nodes.add(new HtmlParseNode(ParseNodeType.ELEMENT, sti, innerContent, ""));
        } else {
            // in this case, We're just looking at inner text content
            String textContent = getTextContent(sr);
            nodes.add(new HtmlParseNode(ParseNodeType.CHARACTERS, null, List.of(), textContent));
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
    public static TagInfo getTagInformation(StringReader sr) throws IOException {
        String tagNameString = getTagName(sr);
        if (tagNameString.length() > 1 && tagNameString.charAt(0) == '/') {
            TagName tagName = TagName.valueOf(tagNameString.substring(1).toUpperCase(Locale.ROOT));
            return new TagInfo(tagName, Map.of(), true);
        } else {
            TagName tagName = TagName.valueOf(tagNameString.toUpperCase(Locale.ROOT));
            return new TagInfo(tagName, Map.of(), false);
        }
    }

    private static String getTagName(StringReader sr) throws IOException {
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

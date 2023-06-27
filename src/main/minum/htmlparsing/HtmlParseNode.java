package minum.htmlparsing;

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
                            String textContent) { }
package minum.htmlparsing;

import minum.exceptions.ForbiddenUseException;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import static minum.utils.Invariants.mustBeTrue;

/**
 * This class converts HTML strings to HTML
 * object trees.
 * <p>
 *     Its main purpose is to support functional testing.  HTML
 *     parsing is intricate because of how flexible
 *     the specification is.  We have no need
 *     to perfectly meet the criteria of the spec, so
 *     this is understood to be a subset of the spec.
 * </p>
 */
public class HtmlParser {

    /**
     * Most total chars we'll read.
     */
    final static int MAX_HTML_SIZE = 2 * 1024 * 1024;

    /**
     * Given any HTML input, scan through and generate a tree
     * of HTML nodes.  Return a list of the roots of the tree.
     * <p>
     * This parser operates with a very particular paradigm in mind. I'll explain
     * it through examples.  Let's look at some typical HTML:
     * </p>
     * <pre>{@code <p>Hello world</p>}</pre>
     * <p>
     * The way we will model this is as follows:
     * </p>
     * <pre>{@code <ELEMENT_NAME_AND_DETAILS>content<END_OF_ELEMENT>}</pre>
     * <p>
     * Basically, we'll examine the first part, "ELEMENT_NAME_AND_DETAILS", and
     * grab the element's name and any attributes.  Then we'll descend into the
     * content section.  We'll know we've hit the end of the element by keeping
     * track of how far we've descended/ascended and whether we are hitting
     * a closing HTML element.
     * </p>
     * <p>
     * Complicating this is that elements may not have content, for example
     * any <a href="https://www.w3.org/TR/2011/WD-html-markup-20110113/syntax.html#void-element_xref3">void elements</a>
     * or when a user chooses to create an empty tag
     * </p>
     */
    public List<HtmlParseNode> parse(String input) {
        if (input.length() > MAX_HTML_SIZE)
            throw new ForbiddenUseException("Input exceeds max allowed HTML text size, " + MAX_HTML_SIZE + " chars");
        StringReader stringReader = new StringReader(input);

        List<HtmlParseNode> nodes = new ArrayList<>();
        State state = State.buildNewState();

        while (true) {
            int value;
            try {
                value = stringReader.read();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // if the value is -1, there's nothing left to read
            if (value < 0) return nodes;

            char currentChar = (char) value;
            processState(currentChar, state, nodes);
        }
    }

    /**
     * Use important symbols in the HTML code to indicate
     * which mode we are in - reading inside a tag, or between
     * tags.
     * <p>
     * Apologies to future readers.  Hand-written parser code is the suck.
     * </p>
     * <p>
     * That said, there are plenty of tests exercising this, and it is
     * easy to test due to having been built using TDD.  Cold comfort, I know.
     * </p>
     */
    private void processState(char currentChar, State state, List<HtmlParseNode> nodes) {

        state.charsRead += 1;

        if (currentChar == '<') {
            /* less-than signs are policed strictly */
            if (state.isInsideAttributeValueQuoted) {
                /*
                Here, we're looking at a less-than that
                is inside a quoted attribute value
                */
                state.stringBuilder.append(currentChar);
            } else {
                enteringTag(state);
            }
        } else if (currentChar == '>') {
            /* It's allowed to use greater-than signs in a lot of places */
            if (state.isInsideTag) {
                if (state.isInsideAttributeValueQuoted) {
                    /*
                    Here, we're looking at a greater-than
                    that is inside a quoted attribute value
                    */
                    state.stringBuilder.append(currentChar);
                } else {
                    if (state.hasEncounteredTagName && state.tagName.isEmpty() && state.stringBuilder.length() > 0) {
                        // We've just finished building the tagname
                        state.tagName = state.stringBuilder.toString();
                    } else if ( state.stringBuilder.length() > 0 && state.currentAttributeKey.isBlank() && state.isReadingAttributeKey) {
                        state.attributes.put(state.stringBuilder.toString(), "");
                        state.stringBuilder = new StringBuilder();
                        state.isReadingAttributeKey = false;
                    } else if (!state.currentAttributeKey.isBlank()) {
                        // if we were in the midst of reading attribute stuff when we hit the closing bracket...
                        if (state.stringBuilder.length() > 0) {
                            state.attributes.put(state.currentAttributeKey, state.stringBuilder.toString());
                        } else {
                            state.attributes.put(state.currentAttributeKey, "");
                        }
                        state.isInsideAttributeValueQuoted = false;
                        state.stringBuilder = new StringBuilder();
                        state.currentAttributeKey = "";
                    }

                    exitingTag(state, nodes);
                }
            } else {
                /*
                This situation means we're looking at a
                free-floating greater-than symbol in
                the html text.
                */
                state.stringBuilder.append(currentChar);
            }
        } else {
            addingToken(state, currentChar);
        }
    }

    /**
     * When we've read a less-than sign and are entering an HTML tag.
     */
    private void enteringTag(State state) {
        if (state.stringBuilder.length() > 0) {

            String textContent = state.stringBuilder.toString();

            // This is where we add characters if we found any between tags.
            if (state.parseStack.size() > 0 && ! textContent.isBlank()) {
                state.parseStack.peek().innerContent().add(new HtmlParseNode(ParseNodeType.CHARACTERS, TagInfo.EMPTY, new ArrayList<>(), textContent));
            }
        }

        state.isInsideTag = true;
        /*
        not really sure it's a start tag, but if we
        assume it is that's fine, because if we hit
        a forward slash at the beginning, it becomes
        a non-start-tag.
         */
        state.isStartTag = true;
        state.stringBuilder = new StringBuilder();
    }

    /**
     * Called when we've just hit a greater-than sign and thus
     * exited an HTML tag.
     */
    private void exitingTag(State state, List<HtmlParseNode> nodes) {
        processTag(state, nodes);

        state.isHalfClosedTag = false;
        state.isInsideTag = false;
        state.isStartTag = false;
        state.isReadingTagName = false;
        state.tagName = "";
        state.attributes = new HashMap<>();
        state.hasEncounteredTagName = false;
        state.stringBuilder = new StringBuilder();
    }

    /**
     * The commonest case when reading characters.  Buckle up.
     */
    private void addingToken(State state, char currentChar) {
        var hasNotBegunReadingTagName = state.isInsideTag && !state.hasEncounteredTagName;

        if (hasNotBegunReadingTagName) {

            if (currentChar == ' ') {
                /*
                At this point, we're inside the tag, and we've encountered whitespace.
                Seeking the tag name (although we may be inside a closing tag).
                 */
                state.stringBuilder = new StringBuilder();
            } else if (currentChar == '/') {
                /*
                hitting a forward-slash symbol means we're looking
                at the closure of a tag
                */
                state.isStartTag = false;
                state.stringBuilder = new StringBuilder();
            } else if (Character.isLetter(currentChar) || Character.isDigit(currentChar)) {

                /*
                Here, our input could definitely be the letters of a tag name
                 */
                state.hasEncounteredTagName = true;
                state.isReadingTagName = true;
                state.stringBuilder.append(currentChar);
            }
        } else if (state.isReadingTagName) {
            if (currentChar == ' ') {
                /*
                At this point, we've been reading the tag name and we've encountered whitespace.
                That means we are done reading the tag name
                 */
                state.hasEncounteredTagName = true;
                state.isReadingTagName = false;
                state.tagName = state.stringBuilder.toString();
                state.attributes = new HashMap<>();
                state.stringBuilder = new StringBuilder();
            } else {
                /*
                Reading the characters of the tag name
                 */
                state.hasEncounteredTagName = true;
                state.tagName = "";
                state.stringBuilder.append(currentChar);
            }
        } else if (!state.tagName.isEmpty() && state.isInsideTag) {
            // at this point we have a tagname for our tag, and we're still in the tag

            /*
            the following logic looks crazy (sorry) but what it's trying to do
            is to check whether we're in the whitespace between the tagname and
            the start of the (potential) key.
            */
            boolean atYetAnotherWhitespaceBetweenTagAndAttributes =
                    state.currentAttributeKey.isEmpty() &&
                    state.stringBuilder.length() == 0
                            && currentChar == ' ';

            // once we cross the chasm between the tag and the potential key,
            // there's a whole set of logic to run through. Yum. (but by the
            // way, it's not uncommon to never see a key inside a tag, so none
            // of this code would ever get run in that case)
            boolean handlingAttributes = !atYetAnotherWhitespaceBetweenTagAndAttributes;
            if (handlingAttributes) {

                if (state.currentAttributeKey.isBlank()) {
                    /*
                    because the key is blank, we know we haven't read it all. That's
                    because when we finish reading the key, we'll add it to currentAttributeKey
                    and be in the mode of reading the value.
                     */
                    if (state.isHalfClosedTag) {
                        /*
                        if we got here, it means the previous char was
                        a forward slash, so the current character *should*
                        be a closing angle, but it's not! So.. panic.
                         */
                        throw new ParsingException("char after forward slash must be angle bracket.  Char: " + currentChar);
                    } else if (currentChar == ' ' || currentChar == '=') {
                        // if we hit whitespace or an equals sign, we're done reading the key
                        state.currentAttributeKey = state.stringBuilder.toString();
                        state.isReadingAttributeKey = false;
                        state.stringBuilder = new StringBuilder();
                    } else if (currentChar == '/') {
                        // a forward-slash cannot be in the attribute key
                        state.isReadingAttributeKey = false;
                        state.isHalfClosedTag = true;
                    } else {
                        // otherwise keep on reading
                        state.stringBuilder.append(currentChar);
                        // and note we are reading the key
                        state.isReadingAttributeKey = true;
                    }
                } else {
                    // reading in the (potential) attribute value

                    if (state.isInsideAttributeValueQuoted) {
                        // if we're already inside a quoted area, encountering a
                        // closing quote will take us out of it.
                        if (currentChar == state.quoteType.literal) {
                            // if we hit the matching end-quote, switch modes
                            state.isInsideAttributeValueQuoted = false;
                            state.quoteType = QuoteType.NONE;
                            state.attributes.put(state.currentAttributeKey, state.stringBuilder.toString());
                            state.stringBuilder = new StringBuilder();
                            state.currentAttributeKey = "";
                            state.isReadingAttributeKey = false;
                        } else {
                            // otherwise keep on trucking, adding characters
                            state.stringBuilder.append(currentChar);
                        }
                    } else {
                        if (currentChar == '"' || currentChar == '\'') {
                            /*
                            if we're not currently inside a quoted area but encounter
                            a quote, switch modes.
                             */
                            state.isInsideAttributeValueQuoted = true;
                            state.quoteType = QuoteType.byLiteral(currentChar);
                        } else if (state.stringBuilder.length() > 0 && currentChar == ' ') {
                            /*
                            if we're not in a quoted area and encounter a space, then
                            we're done reading the attribute value and can add the key-value
                            pair to the map.
                             */
                            state.attributes.put(state.currentAttributeKey, state.stringBuilder.toString());
                            state.isReadingAttributeKey = false;
                            state.stringBuilder = new StringBuilder();
                            state.currentAttributeKey = "";
                        } else {
                            // otherwise keep trucking along adding characters
                            state.stringBuilder.append(currentChar);
                        }
                    }
                }
            }
        } else {
            state.stringBuilder.append(currentChar);
        }
    }

    /**
     * This examines the results of reading a tag - if it's
     * a start tag, it pushes it onto a stack for later
     * comparison to the end tag.  The stack is a key
     * component of how we are able to nest the tags properly.
     */
    private void processTag(State state, List<HtmlParseNode> nodes) {
        String tagNameString = state.tagName;
        TagName tagName;
        String upperCaseToken = tagNameString.toUpperCase(Locale.ROOT);
        try {
            tagName = TagName.valueOf(upperCaseToken);
        } catch (IllegalArgumentException ex) {
            throw new ParsingException("Invalid HTML element: " + upperCaseToken + " at character " + (state.charsRead - (1 + upperCaseToken.length())));
        }
        var tagInfo = new TagInfo(tagName, state.attributes);
        if (state.isStartTag) {
            HtmlParseNode newNode = new HtmlParseNode(ParseNodeType.ELEMENT, tagInfo, new ArrayList<>(), "");

            if (state.parseStack.size() > 0) {
                // if we're inside an html element,
                // add this to the inner content
                state.parseStack.peek().innerContent().add(newNode);
            }

            if (state.parseStack.size() == 0 && tagName.isVoidElement) {
                // if we're at the root level and encountering a void element,
                // add it to the root-level list of nodes
                nodes.add(newNode);
            } else if (!tagName.isVoidElement) {
                state.parseStack.push(newNode);
            }
        } else {
            // if we're leaving an end-tag, it means we have a
            // full element with potentially inner content
            HtmlParseNode htmlParseNode = state.parseStack.pop();

             /*
            If the stack is a size of zero at this point, it means we're at the
            roots of our HTML code, which means it's the proper time to add the
            topmost element we just popped into a list.
             */
            if (state.parseStack.size() == 0) {
                nodes.add(htmlParseNode);
            }
            TagName expectedTagName = htmlParseNode.tagInfo().tagName();
            if (expectedTagName != tagName) {
                throw new ParsingException("Did not find expected element. " + "Expected: " + expectedTagName + " at character " + (state.charsRead - (1 + tagNameString.length())));
            }
        }
    }

    private enum QuoteType {
        SINGLE_QUOTED('\''), DOUBLE_QUOTED('"'), NONE(Character.MIN_VALUE);

        public final char literal;

        QuoteType(char literal) {
            this.literal = literal;
        }

        public static QuoteType byLiteral(char currentChar) {
            mustBeTrue(currentChar == '\'' || currentChar == '"', "There are only two valid characters here.");
            if (currentChar == '\'') {
                return QuoteType.SINGLE_QUOTED;
            } else  {
                return QuoteType.DOUBLE_QUOTED;
            }
        }
    }

    private static class State {
        static State buildNewState() {
            return new State(0, false, new StringBuilder(), new Stack<>(), false, false, true, false, "", "", new HashMap<>(), QuoteType.NONE, false, false);
        }

        /**
         * If we encounter a forward-slash in a tag, and we're
         * not in the midst of reading an attribute value, then
         * we expect the next character to be a greater-than symbol.
         */
        public boolean isHalfClosedTag;
        /**
         * total number of chars read of this HTML file
         */
        int charsRead;
        /**
         * True if we are inside angle brackets (may be a closing tag)
         */
        boolean isInsideTag;
        /**
         * Where we build up tokens a character at a time
         */
        StringBuilder stringBuilder;
        /**
         * A stack of HtmlParseNodes, used to see how far deep in the tree we are
         */
        Stack<HtmlParseNode> parseStack;
        /**
         * True if we have successfully encountered the first letter of the tag
         */
        boolean hasEncounteredTagName;
        /**
         * True if we are in the process of reading the tag (e.g. p, a, h1, etc)
         */
        boolean isReadingTagName;

        /**
         * if we determine we are in the midst of reading an attribute key
         */
        boolean isReadingAttributeKey;

        /**
         * True if we determine we are probably in the start tag (rather than the closing tag)
         */
        boolean isStartTag;
        /**
         * True if we're inside the quoted area inside an attribute value in an element
         * tag - this could be where we encounter some symbols that may not be allowed elsewhere.
         */
        boolean isInsideAttributeValueQuoted;
        /**
         * If we're in a quoted area, it's either single or double-quoted.
         * These quotes need to be paired properly, so we need to keep track.
         */
        QuoteType quoteType;
        /**
         * The string value of the tag name
         */
        String tagName;
        /**
         * The attribute key we just read
         */
        String currentAttributeKey;
        /**
         * a map of string to values (in some cases there won't be an equals
         * sign, meaning the value is null.  In other cases there will be an
         * equals sign but no value, meaning the value is empty string)
         */
        Map<String, String> attributes;

        /**
         * Holds the state so we can remember where we are as we examine the HTML
         * a character at a time.
         */
        public State(int charsRead, boolean isInsideTag, StringBuilder stringBuilder,
                     Stack<HtmlParseNode> parseStack, boolean hasEncounteredTagName, boolean isReadingTagName,
                     boolean isStartTag, boolean isInsideAttributeValueQuoted, String tagName,
                     String currentAttributeKey, Map<String, String> attributes, QuoteType quoteType,
                     boolean isReadingAttributeKey, boolean isHalfClosedTag) {

            this.charsRead = charsRead;
            this.isInsideTag = isInsideTag;
            this.stringBuilder = stringBuilder;
            this.parseStack = parseStack;
            this.hasEncounteredTagName = hasEncounteredTagName;
            this.isReadingTagName = isReadingTagName;
            this.isStartTag = isStartTag;
            this.isInsideAttributeValueQuoted = isInsideAttributeValueQuoted;
            this.tagName = tagName;
            this.currentAttributeKey = currentAttributeKey;
            this.attributes = attributes;
            this.quoteType = quoteType;
            this.isReadingAttributeKey = isReadingAttributeKey;
            this.isHalfClosedTag = isHalfClosedTag;
        }
    }

    /**
     * Searches the node tree. If zero nodes are found, returns HtmlParseNode.EMPTY.
     * If one is found, it is returned.  If more than one is found, an exception is thrown.
     */
    public HtmlParseNode searchOne(List<HtmlParseNode> nodes, TagName tagName, Map<String, String> attributes) {
        List<HtmlParseNode> foundNodes = new ArrayList<>();
        for (var node : nodes) {
            var result = node.searchOne(tagName, attributes);
            if (result != HtmlParseNode.EMPTY) {
                foundNodes.add(result);
            }
        }
        mustBeTrue(foundNodes.size() == 0 || foundNodes.size() == 1, "More than 1 node found.  Here they are:" + foundNodes);
        if (foundNodes.size() == 0) {
            return HtmlParseNode.EMPTY;
        } else {
            return foundNodes.get(0);
        }
    }

}

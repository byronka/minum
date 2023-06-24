package minum.utils;

import minum.exceptions.ForbiddenUseException;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

public class HtmlParser {

    /**
     * Most total chars we'll read.
     */
    final static int MAX_HTML_SIZE = 2 * 1024 * 1024;

    static class State {
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
         * True if we determine we are probably in the start tag (rather than the closing tag)
         */
        boolean isStartTag;

        /**
         * True if we're inside the quoted area inside an attribute value in an element
         * tag - this could be where we encounter some symbols that may not be allowed elsewhere.
         */
        boolean isInsideAttributeValueQuoted;

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
        public State(
                int charsRead,
                boolean isInsideTag,
                StringBuilder stringBuilder,
                Stack<HtmlParseNode> parseStack,
                boolean hasEncounteredTagName,
                boolean isReadingTagName,
                boolean isStartTag,
                boolean isInsideAttributeValueQuoted,
                String tagName,
                String currentAttributeKey,
                Map<String, String> attributes
        ) {

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
        }

        static State INITIAL_STATE = new State(0, false, new StringBuilder(), new Stack<>(), false, false, true, false, "", "", new HashMap<>());
    }

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
    public static List<HtmlParseNode> parse(String input) throws IOException {
        if (input.length() > MAX_HTML_SIZE)
            throw new ForbiddenUseException("Input exceeds max allowed HTML text size, " + MAX_HTML_SIZE + " chars");
        StringReader stringReader = new StringReader(input);

        List<HtmlParseNode> nodes = new ArrayList<>();
        State state = State.INITIAL_STATE;

        while (true) {
            int value = stringReader.read();
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
    private static void processState(char currentChar, State state, List<HtmlParseNode> nodes) {

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
                        state.tagName = state.stringBuilder.toString();
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
    private static void enteringTag(State state) {
        if (state.stringBuilder.length() > 0) {

            String textContent = state.stringBuilder.toString();

            if (state.parseStack.size() > 0) {

                state.parseStack.peek()
                        .innerContent()
                        .add(
                                new HtmlParseNode(
                                        ParseNodeType.CHARACTERS,
                                        TagInfo.EMPTY,
                                        new ArrayList<>(),
                                        textContent));
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
    private static void exitingTag(State state, List<HtmlParseNode> nodes) {
        processTag(state, nodes);

        state.isInsideTag = false;
        state.isStartTag = false;
        state.tagName = "";
        state.attributes = new HashMap<>();
        state.hasEncounteredTagName = false;
        state.stringBuilder = new StringBuilder();
    }

    /**
     * The commonest case when reading characters.  Buckle up.
     */
    private static void addingToken(State state, char currentChar) {
        var hasNotBegunReadingTagName = state.isInsideTag && !state.hasEncounteredTagName;

        if (hasNotBegunReadingTagName) {

            if (currentChar == ' ') {
                /*
                At this point, we're inside the tag and we've encountered whitespace.
                Seeking the tag name (although we may be inside a closing tag.
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
            // if we're reading beyond the tag name

            if (! (state.currentAttributeKey.isEmpty() && state.stringBuilder.length() == 0 && currentChar == ' ')) {
                if (state.currentAttributeKey.isBlank()) {
                    // reading in the attribute key
                    if (currentChar == ' ' || currentChar == '=') {
                        state.currentAttributeKey = state.stringBuilder.toString();
                        state.stringBuilder = new StringBuilder();
                    } else {
                        state.stringBuilder.append(currentChar);
                    }
                } else {
                    // reading in the (potential) attribute value
                    if (state.isInsideAttributeValueQuoted) {
                        if (currentChar == '"' || currentChar == '\'') {
                            state.isInsideAttributeValueQuoted = false;
                            state.attributes.put(state.currentAttributeKey, state.stringBuilder.toString());
                            state.stringBuilder = new StringBuilder();
                            state.currentAttributeKey = "";
                        } else {
                            state.stringBuilder.append(currentChar);
                        }
                    } else {
                        if (currentChar == '"' || currentChar == '\'') {
                            state.isInsideAttributeValueQuoted = true;
                        } else if (state.stringBuilder.length() > 0 && currentChar == ' ') {
                            state.attributes.put(state.currentAttributeKey, state.stringBuilder.toString());
                            state.stringBuilder = new StringBuilder();
                            state.currentAttributeKey = "";
                        } else {
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
    private static void processTag(State state, List<HtmlParseNode> nodes) {
        String tagNameString = state.tagName;
        TagName tagName;
        String upperCaseToken = tagNameString.toUpperCase(Locale.ROOT);
        try {
            tagName = TagName.valueOf(upperCaseToken);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid HTML element: " + upperCaseToken +
                    " at character " + (state.charsRead - (1 + upperCaseToken.length())));
        }
        var tagInfo = new TagInfo(tagName, state.attributes);
        if (state.isStartTag) {
            if (state.parseStack.size() > 0) {
                state.parseStack.peek().innerContent().add(new HtmlParseNode(ParseNodeType.ELEMENT, tagInfo, new ArrayList<>(), ""));
            }
            state.parseStack.push(new HtmlParseNode(ParseNodeType.ELEMENT, tagInfo, new ArrayList<>(), ""));
        } else {
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
                throw new RuntimeException("Did not find expected element. " +
                        "Expected: " + expectedTagName + " at character " + (state.charsRead - (1 + tagNameString.length())));
            }
        }
    }

}

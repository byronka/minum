package com.renomad.minum.htmlparsing;

import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.utils.RingBuffer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Converts HTML strings to object trees.
 * <p>
 *     Enables a developer to analyze an HTML document by its
 *     structure.
 * </p>
 * <p>
 *     Note: HTML parsing is difficult because
 *     of its lenient specification.  See Postel's Law.
 * </p>
 * <p>
 *     For our purposes, it is less important
 *     to perfectly meet the criteria of the spec, so
 *     there will be numerous edge-cases unaccounted-for
 *     by this implementation.  Nevertheless, this program
 *     should suit many needs for ordinary web applications.
 * </p>
 */
public final class HtmlParser {

    /**
     * Most total chars we'll read.
     */
    static final int MAX_HTML_SIZE = 2 * 1024 * 1024;

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
     * We will examine the first part, "ELEMENT_NAME_AND_DETAILS", and
     * grab the element's name and any attributes.  Then we will descend into the
     * content section.  We know we have hit the end of the element by keeping
     * track of how far we have descended/ascended and whether we are hitting
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
        var is = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

        List<HtmlParseNode> nodes = new ArrayList<>();
        State state = State.buildNewState();

        while (true) {
            int value = is.read();
            // if the value is -1, there's nothing left to read
            if (value == -1) return nodes;

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
        recordLocation(currentChar, state);

        // keep track of previous twelve characters, to check if inside comments and scripts
        state.previousCharacters.add(currentChar);
        determineCommentState(state);
        determineScriptState(state);
        if (state.isInsideComment) {
            return;
        }
        if (state.isInsideScript) {
            state.stringBuilder.append(currentChar);
            return;
        }

        if (currentChar == '<') {
            processLessThan(currentChar, state);
        } else if (currentChar == '>') {
            processGreaterThan(currentChar, state, nodes);
        } else {
            addingToken(state, currentChar);
        }
    }

    /**
     * handle basic recording of stats, like row and column,
     * useful during error messages
     */
    private static void recordLocation(char currentChar, State state) {
        state.charsRead += 1;
        if (currentChar == '\n') {
            state.lineRow += 1;
            state.lineColumn = 0;
        }
        state.lineColumn += 1;
    }

    private void processGreaterThan(char currentChar, State state, List<HtmlParseNode> nodes) {
        /* It's allowed to use greater-than signs in a lot of places */
        if (state.isInsideTag) {
            handleExitingTag(currentChar, state, nodes);
        } else {
            /*
            This situation means we're looking at a
            free-floating greater-than symbol in
            the html text.
            */
            state.stringBuilder.append(currentChar);
        }
    }

    /**
     * As we leave the tag, we make some decisions about it.
     */
    private void handleExitingTag(char currentChar, State state, List<HtmlParseNode> nodes) {
        if (state.isInsideAttributeValueQuoted) {
            /*
            Here, we're looking at a greater-than
            that is inside a quoted attribute value
            */
            state.stringBuilder.append(currentChar);
        } else {
            handleTagComponents(state, nodes);
        }
    }

    private void handleTagComponents(State state, List<HtmlParseNode> nodes) {
        if (hasFinishedBuildingTagname(state.hasEncounteredTagName, state.tagName, state.stringBuilder)) {
            state.tagName = state.stringBuilder.toString();
        } else if (!state.stringBuilder.isEmpty() && state.currentAttributeKey.isBlank() && state.isReadingAttributeKey) {
            state.attributes.put(state.stringBuilder.toString(), "");
            state.stringBuilder = new StringBuilder();
            state.isReadingAttributeKey = false;
        } else if (!state.currentAttributeKey.isBlank()) {
            // if we were in the midst of reading attribute stuff when we hit the closing bracket...
            if (!state.stringBuilder.isEmpty()) {
                state.attributes.put(state.currentAttributeKey, state.stringBuilder.toString());
            } else {
                state.attributes.put(state.currentAttributeKey, "");
            }
            state.isInsideAttributeValueQuoted = false;
            state.stringBuilder = new StringBuilder();
            state.currentAttributeKey = "";
        }

        processTagAndResetState(state, nodes);
    }

    static boolean hasFinishedBuildingTagname(boolean hasEncounteredTagName, String tagName, StringBuilder sb) {
        return hasEncounteredTagName && tagName.isEmpty() && !sb.isEmpty();
    }

    private void processLessThan(char currentChar, State state) {
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
    }

    /**
     * When we've read a less-than sign and are entering an HTML tag.
     */
    private void enteringTag(State state) {
        addText(state);

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

    private static void addText(State state) {
        if (!state.stringBuilder.isEmpty()) {

            String textContent = state.stringBuilder.toString();

            // This is where we add characters if we found any between tags.
            if (! state.parseStack.isEmpty() && ! textContent.isBlank()) {
                state.parseStack.peek().addToInnerContent(new HtmlParseNode(ParseNodeType.CHARACTERS, TagInfo.EMPTY, new ArrayList<>(), textContent));
            }
        }
    }

    /**
     * Called when we've just hit a greater-than sign and thus
     * exited an HTML tag.
     */
    private void processTagAndResetState(State state, List<HtmlParseNode> nodes) {
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
            handleBeforeReadingTagName(state, currentChar);
        } else if (state.isReadingTagName) {
            handleReadingTagName(state, currentChar);
        } else if (isFinishedReadingTag(state.tagName, state.isInsideTag)) {
                handleAfterReadingTagName(state, currentChar);
        } else {
            state.stringBuilder.append(currentChar);
        }
    }

    static boolean isFinishedReadingTag(String tagName, boolean isInsideTag) {
        return !tagName.isEmpty() && isInsideTag;
    }

    static final List<Character> startOfComment = List.of('<', '!', '-', '-');
    static final List<Character> endOfComment = List.of('-', '-', '>');

    /**
     * Returns whether we are inside an HTML comment,
     * that is {@code <!-- -->}
     */
    private void determineCommentState(State state) {
        boolean atCommentStart = state.previousCharacters.containsAt(startOfComment, 8);
        boolean atCommentEnd = state.previousCharacters.containsAt(endOfComment, 8);
        boolean isInsideTag = state.isInsideTag;
        boolean hasEncounteredTagName = state.hasEncounteredTagName;
        if (isInsideTag && !hasEncounteredTagName && atCommentStart) {
            state.isInsideComment = true;
            state.isInsideTag = false;
        } else if (state.isInsideComment && atCommentEnd) {
            state.isInsideComment = false;
        }
    }

    static final List<Character> scriptElement = List.of('<','/','s','c','r','i','p','t','>');

    /**
     * Determines whether we have hit the end of the script block
     * by looking for the closing script tag.
     */
    private void determineScriptState(State state) {
        boolean isScriptFinished = state.previousCharacters.containsAt(scriptElement, 3);
        boolean wasInsideScript = state.isInsideScript;
        state.isInsideScript = state.isInsideScript && !isScriptFinished;
        boolean justClosedScriptTag = wasInsideScript && !state.isInsideScript;
        if (justClosedScriptTag) {
            state.tagName = "script";
            state.isInsideTag = true;
            state.isStartTag = false;
            var innerTextLength = state.stringBuilder.length();
            state.stringBuilder.delete(innerTextLength - 8, innerTextLength);
            addText(state);
        }

    }

    /**
     * at this point we have a tagname for our tag, and we're still in the tag
     */
    private static void handleAfterReadingTagName(State state, char currentChar) {

        boolean isHandlingAttributes = isHandlingAttributes(state, currentChar);
        if (isHandlingAttributes) {

            if (state.currentAttributeKey.isBlank()) {
                /*
                because the key is blank, we know we haven't read it all. That's
                because when we finish reading the key, we'll add it to currentAttributeKey
                and be in the mode of reading the value.
                 */
                handleNotFullyReadAttributeKey(state, currentChar);
            } else {
                // reading in the (potential) attribute value

                handlePotentialAttributeValue(state, currentChar);
            }
        }
    }

    /**
     * Check whether we're past the whitespace between the tag name and
     * the start of the (potential) attribute key.
     */
    static boolean isHandlingAttributes(State state, char currentChar) {
        return ! (state.currentAttributeKey.isEmpty() &&
                        state.stringBuilder.isEmpty()
                        && currentChar == ' ');
    }

    private static void handlePotentialAttributeValue(State state, char currentChar) {
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
            } else if (!state.stringBuilder.isEmpty() && currentChar == ' ') {
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

    private static void handleNotFullyReadAttributeKey(State state, char currentChar) {
        if (state.isHalfClosedTag) {
            /*
            This situation occurs when we are in a void tag, like <link />,
            and are closing the tag with a forward slash + closing bracket.

            if we got here, it means the previous char was
            a forward slash, so the current character *should*
            be a closing angle, but if it's not ...
             */
            throw new ParsingException(String.format("in closing a void tag (e.g. <link />), character after forward slash must be angle bracket.  Char: %s at line %d and at the %d character. %d chars read in total.", currentChar, state.lineRow, state.lineColumn, state.charsRead));
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
    }

    private static void handleReadingTagName(State state, char currentChar) {
        if (Character.isWhitespace(currentChar)) {
            /*
            At this point, we've been reading the tag name, and we've encountered whitespace.
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
    }

    /**
     * We're just past a starting angle bracket, so we're
     * feeling our way around what this element is.
     */
    private static void handleBeforeReadingTagName(State state, char currentChar) {
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
        } else if (Character.isAlphabetic(currentChar)) {

            /*
            Here, our input could definitely be the letters of a tag name
             */
            state.hasEncounteredTagName = true;
            state.isReadingTagName = true;
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

        tagName = TagName.findMatchingTagname(tagNameString);
        if (tagName.equals(TagName.UNRECOGNIZED)) return;
        var tagInfo = new TagInfo(tagName, state.attributes);
        if (state.isStartTag) {
            HtmlParseNode newNode = new HtmlParseNode(ParseNodeType.ELEMENT, tagInfo, new ArrayList<>(), "");

            if (! state.parseStack.isEmpty()) {
                // if we're inside an html element,
                // add this to the inner content
                state.parseStack.peek().addToInnerContent(newNode);
            }

            if (state.parseStack.isEmpty() && tagName.isVoidElement) {
                // if we're at the root level and encountering a void element,
                // add it to the root-level list of nodes
                nodes.add(newNode);
            } else if (!tagName.isVoidElement) {
                state.parseStack.push(newNode);
            }

            if (tagName.equals(TagName.SCRIPT)) {
                state.isInsideScript = true;
                state.stringBuilder = new StringBuilder();
            }
        } else {
            // if we're leaving an end-tag, it means we have a
            // full element with potentially inner content
            HtmlParseNode htmlParseNode;
            try {
                htmlParseNode = state.parseStack.pop();
            } catch (NoSuchElementException ex) {
                throw new ParsingException("No starting tag found. At line " + state.lineRow + " and at the " + state.lineColumn + "th character. " + state.charsRead + " characters read in total.");
            }

             /*
            If the stack is a size of zero at this point, it means we're at the
            roots of our HTML code, which means it's the proper time to add the
            topmost element we just popped into a list.
             */
            if (state.parseStack.isEmpty()) {
                nodes.add(htmlParseNode);
            }
            TagName expectedTagName = htmlParseNode.getTagInfo().getTagName();
            if (expectedTagName != tagName) {
                throw new ParsingException("Did not find expected closing-tag type. " + "Expected: " + expectedTagName + " at line " + state.lineRow + " and at the " + state.lineColumn + "th character. " + state.charsRead + " characters read in total.");
            }
        }
    }

    enum QuoteType {
        SINGLE_QUOTED('\''), DOUBLE_QUOTED('"'), NONE(Character.MIN_VALUE);

        public final char literal;

        QuoteType(char literal) {
            this.literal = literal;
        }

        public static QuoteType byLiteral(char currentChar) {
            if (currentChar == '\'') {
                return QuoteType.SINGLE_QUOTED;
            } else  {
                return QuoteType.DOUBLE_QUOTED;
            }
        }
    }

    static class State {

        static State buildNewState() {
            RingBuffer<Character> previousCharacters = new RingBuffer<>(12, Character.class);
            int lineColumn1 = 0;
            int lineRow1 = 1;
            boolean isHalfClosedTag1 = false;
            boolean isInsideAttributeValueQuoted1 = false;
            boolean isStartTag1 = true;
            boolean isReadingTagName1 = false;
            boolean hasEncounteredTagName1 = false;
            ArrayDeque<HtmlParseNode> parseStack1 = new ArrayDeque<>();
            StringBuilder stringBuilder1 = new StringBuilder();
            boolean isInsideTag1 = false;
            int charsRead1 = 0;
            String tagName1 = "";
            String currentAttributeKey1 = "";
            HashMap<String, String> attributes1 = new HashMap<>();
            boolean isReadingAttributeKey1 = false;
            boolean isInsideComment1 = false;
            boolean isInsideScript1 = false;
            return new State(charsRead1, isInsideTag1, stringBuilder1, parseStack1, hasEncounteredTagName1,
                    isReadingTagName1, isStartTag1, isInsideAttributeValueQuoted1,
                    tagName1, currentAttributeKey1, attributes1, QuoteType.NONE, isReadingAttributeKey1,
                    isHalfClosedTag1, lineRow1, lineColumn1, previousCharacters, isInsideComment1, isInsideScript1);
        }

        /**
         * If we encounter a forward-slash in a tag, and we're
         * not in the midst of reading an attribute value, then
         * we expect the next character to be a greater-than symbol.
         */
        boolean isHalfClosedTag;
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
        final Deque<HtmlParseNode> parseStack;
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
         * indicate which line we're on in debugging
         */
        int lineRow;

        /**
         * How far we are from the last newline character, including
         * all whitespace as well.
         */
        int lineColumn;

        /**
         * This is used to check for comments and script tags, like:
         *     {@code <!-- -->} and {@code <script>}
         */
        final RingBuffer<Character> previousCharacters;

        /**
         * Indicates whether we are inside a comment
         */
        boolean isInsideComment;

        boolean isInsideScript;

        /**
         * Holds the state so we can remember where we are as we examine the HTML
         * a character at a time.
         */
        public State(int charsRead, boolean isInsideTag, StringBuilder stringBuilder,
                     Deque<HtmlParseNode> parseStack, boolean hasEncounteredTagName, boolean isReadingTagName,
                     boolean isStartTag, boolean isInsideAttributeValueQuoted, String tagName,
                     String currentAttributeKey, Map<String, String> attributes, QuoteType quoteType,
                     boolean isReadingAttributeKey, boolean isHalfClosedTag, int lineRow, int lineColumn,
                     RingBuffer<Character> previousCharacters, boolean isInsideComment, boolean isInsideScript) {

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
            this.lineRow = lineRow;
            this.lineColumn = lineColumn;
            this.previousCharacters = previousCharacters;
            this.isInsideComment = isInsideComment;
            this.isInsideScript = isInsideScript;
        }
    }

    /**
     * Search the node tree for matching elements.
     * <p>
     * If zero nodes are found, returns an empty list.
     * </p>
     */
    public List<HtmlParseNode> search(List<HtmlParseNode> nodes, TagName tagName, Map<String, String> attributes) {
        List<HtmlParseNode> foundNodes = new ArrayList<>();
        for (var node : nodes) {
            var result = node.search(tagName, attributes);
            foundNodes.addAll(result);
        }
        return foundNodes;
    }

}

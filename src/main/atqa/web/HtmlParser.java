package atqa.web;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static atqa.web.HtmlParser.ActionType.ADDING_TO_TOKEN;

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
//    public static List<HtmlParseNode> parse(String input) throws IOException {
//        if (input.length() > MAX_HTML_SIZE)
//            throw new ForbiddenUseException("We will not parse a string larger than MAX_HTML_SIZE: " + MAX_HTML_SIZE + " characters");
//        var sr = new StringReader(input);
//        return innerParser(sr, 0);
//    }

//    public static List<HtmlParseNode> innerParser(StringReader sr, int depth) throws IOException {
//        List<HtmlParseNode> nodes = new ArrayList<>();
//        int readResult = sr.read();
//        if (readResult < 0) return List.of();
//        char currentChar = (char) readResult;
//        if (currentChar == '<') {
//            // See https://www.w3.org/TR/2011/WD-html-markup-20110113/syntax.html#syntax-start-tags
//            TagInfo sti = getTagInformation(sr);
//            if (sti.isClosingTag()) return List.of();
//            List<HtmlParseNode> innerContent = new ArrayList<>();
//            if (! sti.tagName().isVoidElement) {
//                innerContent = innerParser(sr, depth + 1);
//            }
//            nodes.add(new HtmlParseNode(ParseNodeType.ELEMENT, sti, innerContent, ""));
//        } else {
//            // in this case, We're just looking at inner text content
//            String textContent = currentChar + getRestOfTextContent(sr);
//            nodes.add(new HtmlParseNode(ParseNodeType.CHARACTERS, null, List.of(), textContent));
//        }
//        return nodes;
//    }

//    private static String getRestOfTextContent(StringReader sr) throws IOException {
//        StringBuilder sb = new StringBuilder();
//
//        for (int i = 0; i < MAX_CHARACTER_CONTENT_SIZE; i++) {
//            if (i == MAX_CHARACTER_CONTENT_SIZE - 1) throw new ForbiddenUseException("Ceasing to parse HTML inner text content - too large");
//            int returnValue = sr.read();
//            if (returnValue < 0) break;
//            char currentChar = (char) returnValue;
//            if (currentChar == '<')  {
//                break;
//            } else {
//                sb.append(currentChar);
//            }
//        }
//        return sb.toString();
//    }

    /**
     * Grab the details
     */
//    public static TagInfo getTagInformation(StringReader sr) throws IOException {
//        String tagNameString = getTagName(sr);
//        if (tagNameString.length() > 1 && tagNameString.charAt(0) == '/') {
//            TagName tagName = TagName.valueOf(tagNameString.substring(1).toUpperCase(Locale.ROOT));
//            return new TagInfo(tagName, Map.of(), true);
//        } else {
//            TagName tagName = TagName.valueOf(tagNameString.toUpperCase(Locale.ROOT));
//            return new TagInfo(tagName, Map.of(), false);
//        }
//    }

//    private static String getTagName(StringReader sr) throws IOException {
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < MAX_ELEMENT_NAME_SIZE; i++) {
//            if (i == MAX_ELEMENT_NAME_SIZE - 1) throw new ForbiddenUseException("Ceasing to parse HTML element name - too large");
//            int currentChar = sr.read();
//            if (currentChar < 0) break;
//            if ((char) currentChar == ' ' || (char) currentChar == '>')  {
//                break;
//            } else {
//                sb.append((char) currentChar);
//            }
//        }
//        return sb.toString();
//    }

    record State(
            boolean isInsideTag,
            StringBuilder stringBuilder,
            int treeDepth,
            int tokenCount,
            boolean isHalfClosedTag){
        static State EMPTY = new State(false, new StringBuilder(), 0, 0, false);
    }

    enum ActionType {
        ENTERING_TAG,
        EXITING_TAG,
        ADDING_TO_TOKEN
    }
    record Action(ActionType actionType, char nextChar){
        static Action ENTERING_TAG = new Action(ActionType.ENTERING_TAG, '<');
        static Action EXITING_TAG = new Action(ActionType.EXITING_TAG, '>');
    }

    /**
     * Outermost parser - convert string to a StringReader
     */
    public static List<HtmlParseNode> parse2(String input) throws IOException {
        StringReader stringReader = new StringReader(input);

        return parse3(stringReader);
    }

    /**
     * At this level, instantiate List of Nodes
     * and loop through characters
     */
    private static List<HtmlParseNode> parse3(StringReader stringReader) throws IOException {
        List<HtmlParseNode> nodes = new ArrayList<>();
        State state = State.EMPTY;

        while(true) {
            int value = stringReader.read();
            // if the value is -1, there's nothing left to read
            if (value < 0) return nodes;

            char currentChar = (char) value;
            Action action = parse4(currentChar);
            state = processState(state, action, nodes);
        }
    }

    private static Action parse4(char currentChar) {
        if (currentChar == '<') {
            return Action.ENTERING_TAG;
        } else if (currentChar == '>') {
            return Action.EXITING_TAG;
        } else {
            return new Action(ADDING_TO_TOKEN, currentChar);
        }
    }

    private static State processState(State state, Action action, List<HtmlParseNode> nodes) {
        switch (action.actionType()) {
            case ENTERING_TAG -> {
                if (state.stringBuilder().length() > 0) {
                    String token = state.stringBuilder().toString();
                    nodes.add(new HtmlParseNode(ParseNodeType.CHARACTERS, TagInfo.EMPTY, List.of(), token));
                }

                return new State(
                        true,
                        new StringBuilder(),
                        state.treeDepth() + 1,
                        state.tokenCount(),
                        state.isHalfClosedTag());
            }
            case EXITING_TAG -> {
                if (state.stringBuilder().length() > 0) {
                    if (state.isHalfClosedTag()) {
                        return new State(
                                false,
                                new StringBuilder(),
                                state.treeDepth() - 1,
                                0,
                                false
                        );
                    }
                    String token = state.stringBuilder().toString();
                    TagName tagName = TagName.valueOf(token.toUpperCase(Locale.ROOT));
                    var tagInfo = new TagInfo(tagName, Map.of());
                    nodes.add(new HtmlParseNode(ParseNodeType.ELEMENT, tagInfo, List.of(), ""));
                }

                return new State(
                        false,
                        new StringBuilder(),
                        state.treeDepth(),
                        state.tokenCount(),
                        state.isHalfClosedTag());
            }
            case ADDING_TO_TOKEN -> {
                var isReadingTagName = state.isInsideTag() && state.tokenCount() == 0;

                if (isReadingTagName) {

                    var nextChar = action.nextChar();

                    if (nextChar == '/') {
                        return new State(
                                true,
                                new StringBuilder(),
                                state.treeDepth(),
                                0,
                                true);
                    }

                    state.stringBuilder().append(nextChar);

                }

                return new State(
                        state.isInsideTag(),
                        state.stringBuilder(),
                        state.treeDepth(),
                        state.tokenCount(),
                        state.isHalfClosedTag());
            }
        }
        return state;
    }

}

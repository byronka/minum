package atqa.web;

import atqa.exceptions.ForbiddenUseException;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import static atqa.web.HtmlParser.ActionType.ADDING_TO_TOKEN;

public class HtmlParser {

    /**
     * Most total chars we'll read.
     */
    final static int MAX_HTML_SIZE = 2 * 1024 * 1024;

    record State(
            int charsRead,
            boolean isInsideTag,
            StringBuilder stringBuilder,
            Stack<HtmlParseNode> parseStack,
            int tokenCount,
            boolean isStartTag){
        static State INITIAL_STATE = new State(0,false, new StringBuilder(), new Stack<>(), 0, true);
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
     * Given any HTML input, scan through and generate a tree
     * of HTML nodes.  Return a list of the roots of the tree.
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
        if (input.length() > MAX_HTML_SIZE) throw new ForbiddenUseException("Input exceeds max allowed HTML text size, " + MAX_HTML_SIZE + " chars");
        StringReader stringReader = new StringReader(input);

        List<HtmlParseNode> nodes = new ArrayList<>();
        State state = State.INITIAL_STATE;

        while(true) {
            int value = stringReader.read();
            // if the value is -1, there's nothing left to read
            if (value < 0) return nodes;

            char currentChar = (char) value;
            Action action = determineMode(currentChar);
            var newState = new State(
                    state.charsRead + 1,
                    state.isInsideTag(),
                    state.stringBuilder(),
                    state.parseStack(),
                    state.tokenCount(),
                    state.isStartTag()
            );
            state = processState(newState, action, nodes);
        }
    }

    /**
     * Use important symbols in the HTML code to indicate
     * which mode we are in - reading inside a tag, or between
     * tags.
     */
    private static Action determineMode(char currentChar) {
        if (currentChar == '<') {
            return Action.ENTERING_TAG;
        } else if (currentChar == '>') {
            return Action.EXITING_TAG;
        } else {
            return new Action(ADDING_TO_TOKEN, currentChar);
        }
    }

    /**
     * This program takes in an {@link Action}, helping to indicate what
     * kind of symbol we're examining, a {@link State} object which holds
     * some important state as we go, and a list of {@link HtmlParseNode}
     * that we'll occasionally add to (and return as the top of the html tree)
     */
    private static State processState(State state, Action action, List<HtmlParseNode> nodes) {
        switch (action.actionType()) {

            case ENTERING_TAG -> {
                if (state.stringBuilder().length() > 0) {
                    String token = state.stringBuilder().toString();
                    if (state.parseStack().size() > 0) {
                        state.parseStack().peek().innerContent().add(new HtmlParseNode(ParseNodeType.CHARACTERS, TagInfo.EMPTY, new ArrayList<>(), token));
                    }
                }

                return new State(
                        state.charsRead(),
                        true,
                        new StringBuilder(),
                        state.parseStack(),
                        state.tokenCount(),
                        state.isStartTag());
            }
            case EXITING_TAG -> {
                String token = state.stringBuilder().toString();
                TagName tagName;
                String upperCaseToken = token.toUpperCase(Locale.ROOT);
                try {
                    tagName = TagName.valueOf(upperCaseToken);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Invalid HTML element: " + upperCaseToken +
                            " at character " + (state.charsRead() - (1 + upperCaseToken.length())));
                }
                var tagInfo = new TagInfo(tagName, Map.of());
                if (state.isStartTag()) {
                    if (state.parseStack().size() > 0) {
                        state.parseStack().peek().innerContent().add(new HtmlParseNode(ParseNodeType.ELEMENT, tagInfo, new ArrayList<>(), ""));
                    }
                    state.parseStack().push(new HtmlParseNode(ParseNodeType.ELEMENT, tagInfo, new ArrayList<>(), ""));
                } else {
                    HtmlParseNode htmlParseNode = state.parseStack().pop();

                     /*
                    If the stack is a size of zero at this point, it means we're at the
                    roots of our HTML code, which means it's the proper time to add the
                    topmost element we just popped into a list.
                     */
                    if (state.parseStack().size() == 0) {
                        nodes.add(htmlParseNode);
                    }
                    TagName expectedTagName = htmlParseNode.tagInfo().tagName();
                    if (expectedTagName != tagName) {
                        throw new RuntimeException("Did not find expected element. " +
                                "Expected: " + expectedTagName + " at character " + (state.charsRead() - (1 + token.length())));
                    }
                }

                return new State(
                        state.charsRead(),
                        false,
                        new StringBuilder(),
                        state.parseStack(),
                        state.tokenCount(),
                        true);
            }

            case ADDING_TO_TOKEN -> {
                var nextChar = action.nextChar();

                var isReadingTagName = state.isInsideTag() && state.tokenCount() == 0;

                if (isReadingTagName) {

                    /*
                    hitting a forward-slash symbol means we're looking
                    at the closure of a tag
                    */
                    if (nextChar == '/') {
                        return new State(
                                state.charsRead(),
                                true,
                                new StringBuilder(),
                                state.parseStack(),
                                0,
                                false);
                    }
                }

                state.stringBuilder().append(nextChar);

                return new State(
                        state.charsRead(),
                        state.isInsideTag(),
                        state.stringBuilder(),
                        state.parseStack(),
                        state.tokenCount(),
                        state.isStartTag());
            }
        }
        return state;
    }

}

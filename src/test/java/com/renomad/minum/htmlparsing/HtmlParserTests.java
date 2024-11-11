package com.renomad.minum.htmlparsing;

import com.renomad.minum.SearchHelpers;
import com.renomad.minum.state.Context;
import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.utils.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.renomad.minum.htmlparsing.HtmlParser.MAX_HTML_SIZE;
import static com.renomad.minum.testing.TestFramework.*;

public class HtmlParserTests {
    private static FileUtils fileUtils;
    private static Context context;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        fileUtils = new FileUtils(context.getLogger(), context.getConstants());
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    @Test
    public void test_HtmlParser_Details1() {
        String input = "<br foo=bar />";
        var expected = List.of(
                new HtmlParseNode(
                        ParseNodeType.ELEMENT,
                        new TagInfo(TagName.BR, Map.of("foo", "bar")),
                        List.of(),
                        ""));
        List<HtmlParseNode> result = new HtmlParser().parse(input);
        assertEquals(expected, result);
    }

    /**
     * In this situation, the only content to read
     * is the DOCTYPE declaration
     */
    @Test
    public void test_HtmlParser_Doctype() {
        String input = "<!DOCTYPE html>";
        var expected = List.of(
                new HtmlParseNode(
                        ParseNodeType.ELEMENT,
                        new TagInfo(TagName.DOCTYPE, Map.of("html", "")),
                        List.of(),
                        ""));
        List<HtmlParseNode> result = new HtmlParser().parse(input);
        assertEquals(expected, result);
    }

    @Test
    public void test_HtmlParser_Details2() {
        String input = "<title>Stock Prices</title>";
        var expected = List.of(
                new HtmlParseNode(
                        ParseNodeType.ELEMENT,
                        new TagInfo(TagName.TITLE, Map.of()),
                        List.of(new HtmlParseNode(
                                ParseNodeType.CHARACTERS,
                                TagInfo.EMPTY,
                                List.of(),
                                "Stock Prices"
                        )),
                        ""));
        List<HtmlParseNode> result = new HtmlParser().parse(input);
        assertEquals(expected, result);
    }

    @Test
    public void test_HtmlParser_HappyPath() {
        String input = "<!DOCTYPE html><p class=\"baz < > biz\" id=\"wut\" fee=fi>foo ><h1 ></h1></p><p id=></p><br foo=bar />";
        String inputWithSingleTicks = "<!DOCTYPE html><p class='baz < > biz' id='wut' fee=fi>foo ><h1 ></h1></p><p id=></p><br foo=bar />";
        var expected = List.of(
                new HtmlParseNode(
                        ParseNodeType.ELEMENT,
                        new TagInfo(TagName.DOCTYPE, Map.of("html", "")),
                        List.of(),
                        ""),
                new HtmlParseNode(
                        ParseNodeType.ELEMENT,
                        new TagInfo(TagName.P,
                                Map.of(
                                        "class", "baz < > biz",
                                        "id", "wut",
                                        "fee", "fi")),
                        List.of(new HtmlParseNode(
                                        ParseNodeType.CHARACTERS,
                                        new TagInfo(TagName.NULL, Map.of()),
                                        List.of(),
                                        "foo >"),
                                new HtmlParseNode(
                                        ParseNodeType.ELEMENT,
                                        new TagInfo(TagName.H1, Map.of()),
                                        List.of(),
                                        "")
                        ),
                        ""),
                new HtmlParseNode(
                        ParseNodeType.ELEMENT,
                        new TagInfo(TagName.P, Map.of("id", "")),
                        List.of(),
                        ""),
                new HtmlParseNode(
                        ParseNodeType.ELEMENT,
                        new TagInfo(TagName.BR, Map.of("foo", "bar")),
                        List.of(),
                        ""));

        var nodes = new HtmlParser().parse(input);
        var nodesWithSingleTicks = new HtmlParser().parse(inputWithSingleTicks);
        assertEquals(expected, nodes);
        assertEquals(expected, nodesWithSingleTicks);
    }

    /**
     * It's possible to add a comment.
     */
    @Test
    public void testCommentedText() {
        String input = "<p foo=bar><!-- a --></p>";
        var expected = List.of(
                new HtmlParseNode(
                        ParseNodeType.ELEMENT,
                        new TagInfo(TagName.P, Map.of("foo", "bar")),
                        List.of(),
                        ""));
        List<HtmlParseNode> result = new HtmlParser().parse(input);
        assertEquals(expected, result);
    }

    /*
        ********************
             EDGE CASES
        ********************

     */

    @Test
    public void test_HtmlParser_Edge_SpaceBeforeTagname() {
        var result = new HtmlParser().parse("<  p></p>");
        assertEquals(result, List.of(new HtmlParseNode(ParseNodeType.ELEMENT, new TagInfo(TagName.P, Map.of()), List.of(), "")));
    }

    @Test
    public void test_HtmlParser_Edge_InvalidClosingTag() {
        assertThrows(ParsingException.class,
                "Did not find expected closing-tag type. Expected: P at line 1 and at the 8th character. 8 characters read in total.",
                () -> new HtmlParser().parse("<p></br>"));
    }

    /**
     * invalid character after forward slash in start tag
     */
    @Test
    public void test_HtmlParser_Edge_InvalidChar2() {
        assertThrows(ParsingException.class,
                "in closing a void tag (e.g. <link />), character after forward slash must be angle bracket.  Char: / at line 1 and at the 13 character. 13 chars read in total.",
                () -> new HtmlParser().parse("<br id=foo //>"));

        assertThrows(ParsingException.class,
                "in closing a void tag (e.g. <link />), character after forward slash must be angle bracket.  Char: / at line 3 and at the 4 character. 15 chars read in total.",
                () -> new HtmlParser().parse("<br id=foo\n\n //>"));
    }

    @Test
    public void test_HtmlParser_Edge_LargerFile() {
        String htmlText = fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/expected_stock_output.html");

        List<HtmlParseNode> htmlRoots = new HtmlParser().parse(htmlText);
        List<List<String>> myList = htmlRoots.stream().map(SearchHelpers::print).filter(x -> ! x.isEmpty()).toList();

        var expected = fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/expected_stock_output_parsed.txt");
        assertEquals(myList.toString(), expected);
    }

    @Test
    public void test_fuzzer() {
        String htmlText = fileUtils.readTextFile("src/test/resources/html_fuzzer.html");
        List<HtmlParseNode> parsedNodes = new HtmlParser().parse(htmlText);
        HtmlParseNode firstPara = SearchHelpers.search(parsedNodes, TagName.P, Map.of("id", "testing-target")).getFirst();
        assertEquals(SearchHelpers.innerText(firstPara), "Stack Overflow for Teams has its own domain!");
    }


    @Test
    public void test_HtmlParser_Edge_LargerFile_DEPRECATED() {
        String htmlText = fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/expected_stock_output.html");
        List<HtmlParseNode> htmlRoots = new HtmlParser().parse(htmlText);
        List<List<String>> myList = htmlRoots.stream().map(HtmlParseNode::print).filter(x -> ! x.isEmpty()).toList();
        var expected = fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/expected_stock_output_parsed.txt");
        assertEquals(myList.toString(), expected);
    }

    @Test
    public void test_fuzzer_DEPRECATED() {
        String htmlText = fileUtils.readTextFile("src/test/resources/html_fuzzer.html");
        List<HtmlParseNode> htmlRoots = new HtmlParser().parse(htmlText);
        String firstParagraph = htmlRoots.get(1).search(TagName.P, Map.of()).getFirst().innerText();
        assertEquals(firstParagraph, "Stack Overflow for Teams has its own domain!");
    }

    @Test
    public void test_HtmlParser_Edge_NewlineAfterTagname() {
        String html = """
                <button
                        type="button"
                        class="delete_button"
                        personid="3b2fef36-338e-4a57-b327-f42c8ae8896a">Delete</button>
                """;
        List<HtmlParseNode> htmlRoots = new HtmlParser().parse(html);
        assertEquals(htmlRoots.size(), 1);
        assertEquals(htmlRoots.getFirst().getTagInfo().getTagName(), TagName.BUTTON);
    }

    /**
     * If the parser is given content that exceeds the
     * maximum size, it will throw a {@link ForbiddenUseException},
     * which will put the offending client in the brig.
     */
    @Test
    public void test_HtmlParser_ExceedMaxSize() {
        new HtmlParser().parse("a".repeat(MAX_HTML_SIZE - 1));
        new HtmlParser().parse("a".repeat(MAX_HTML_SIZE));
        assertThrows(ForbiddenUseException.class, () -> new HtmlParser().parse("a".repeat(MAX_HTML_SIZE + 1)));
    }

    /**
     * When parsing fails, the error message will show the row
     * and column of the exact character where it was first noticed.
     */
    @Test
    public void test_HtmlParser_ErrorMessagesShowRowAndColumn_NoStartingTag() {
        assertThrows(ParsingException.class,
                "No starting tag found. At line 1 and at the 4th character. 4 characters read in total.",
                () -> new HtmlParser().parse("</p>"));


        assertThrows(ParsingException.class,
                "No starting tag found. At line 4 and at the 5th character. 7 characters read in total.",
                () -> new HtmlParser().parse("\n\n\n</p>"));
    }

    @Test
    public void test_HtmlParser_ErrorMessagesShowRowAndColumn_WrongEndingTag() {
        assertThrows(ParsingException.class,
                "Did not find expected closing-tag type. Expected: A at line 1 and at the 7th character. 7 characters read in total.",
                () -> new HtmlParser().parse("<a></p>"));

        assertThrows(ParsingException.class,
                "Did not find expected closing-tag type. Expected: A at line 4 and at the 5th character. 10 characters read in total.",
                () -> new HtmlParser().parse("<a>\n\n\n</p>"));
    }

    /**
     * If we see a script tag, we collect all its inner data
     */
    @Test
    public void test_HtmlParser_Script() {
        List<HtmlParseNode> parsedNodes = new HtmlParser().parse("<p><script>hello world</script></p>");
        HtmlParseNode script = SearchHelpers.search(parsedNodes, TagName.SCRIPT, Map.of()).getFirst();
        assertEquals(SearchHelpers.innerText(script), "hello world");
    }

    @Test
    public void test_HtmlParser_ScriptWithAttributes() {
        List<HtmlParseNode> parsedNodes = new HtmlParser().parse("<p><script type=text/javascript>hello world</script></p>");
        HtmlParseNode script = SearchHelpers.search(parsedNodes, TagName.SCRIPT, Map.of()).getFirst();
        assertEquals(SearchHelpers.innerText(script), "hello world");
    }

    @Test
    public void test_HtmlParser_ScriptWithAttributes_NoInnerText() {
        List<HtmlParseNode> parsedNodes = new HtmlParser().parse("<p><script type=text/javascript></script></p>");
        HtmlParseNode script = SearchHelpers.search(parsedNodes, TagName.SCRIPT, Map.of("type", "text/javascript")).getFirst();
        assertEquals(SearchHelpers.innerText(script), "");
    }

    /**
     * If we see a script tag, we collect all its inner data
     */
    @Test
    public void test_HtmlParser_Script_DEPRECATED() {
        List<HtmlParseNode> parse = new HtmlParser().parse("<p><script>hello world</script></p>");
        HtmlParseNode script = parse.getFirst().search(TagName.SCRIPT, Map.of()).getFirst();
        assertEquals(script.innerText(), "hello world");
    }

    @Test
    public void test_HtmlParser_ScriptWithAttributes_DEPRECATED() {
        List<HtmlParseNode> parse = new HtmlParser().parse("<p><script type=text/javascript>hello world</script></p>");
        HtmlParseNode script = parse.getFirst().search(TagName.SCRIPT, Map.of()).getFirst();
        assertEquals(script.innerText(), "hello world");
    }

    @Test
    public void test_HtmlParser_ScriptWithAttributes_NoInnerText_DEPRECATED() {
        List<HtmlParseNode> parse = new HtmlParser().parse("<p><script type=text/javascript></script></p>");
        HtmlParseNode script = parse.getFirst().search(TagName.SCRIPT, Map.of("type", "text/javascript")).getFirst();
        assertEquals(script.innerText(), "");
    }

    @Test
    public void test_HtmlParser_SingleQuote() {
        HtmlParseNode paragraphElement = new HtmlParser().parse("<p foo='bar'></p>").getFirst();
        assertEquals(paragraphElement.getTagInfo().getAttribute("foo"), "bar");
    }

    @Test
    public void test_hasFinishedBuildingTagname() {
        StringBuilder foo = new StringBuilder("foo");
        StringBuilder empty = new StringBuilder();
        assertTrue(HtmlParser.hasFinishedBuildingTagname(true, "", foo));
        assertFalse(HtmlParser.hasFinishedBuildingTagname(true, "foo", empty));
        assertFalse(HtmlParser.hasFinishedBuildingTagname(true, "foo", foo));
        assertFalse(HtmlParser.hasFinishedBuildingTagname(true, "", empty));
        assertFalse(HtmlParser.hasFinishedBuildingTagname(false, "", foo));
        assertFalse(HtmlParser.hasFinishedBuildingTagname(false, "foo", empty));
        assertFalse(HtmlParser.hasFinishedBuildingTagname(false, "foo", foo));
        assertFalse(HtmlParser.hasFinishedBuildingTagname(false, "", empty));
    }

    @Test
    public void test_isFinishedReadingTag() {
        assertTrue(HtmlParser.isFinishedReadingTag("foo", true));
        assertFalse(HtmlParser.isFinishedReadingTag("", true));
        assertFalse(HtmlParser.isFinishedReadingTag("foo", false));
        assertFalse(HtmlParser.isFinishedReadingTag("", false));
    }

    /**
     * A test for a logical predicate to determine whether we have
     * moved from whitespace to characters indicating the key of
     * an HTML attribute.
     * <br>
     * The state that we are checking is:
     * <pre>
     *     1. state.currentAttributeKey - a token (i.e. a string) that is probably an HTML attribute
     *     2. state.stringBuilder - an accumulator of text as we scan across characters, often representing
     *                              the most recent important token.
     *     3. currentChar - the current character we're analyzing
     * </pre>
     */
    @Test
    public void test_isHandlingAttributes() {
        HtmlParser.State state = HtmlParser.State.buildNewState();

        // empty attribute key and stringbuilder. This is the only
        // situation where we decide we are still reading whitespace
        // between the tag name and the potential start of an
        // HTML tag attribute
        state.currentAttributeKey = "";
        state.stringBuilder = new StringBuilder();
        assertFalse(HtmlParser.isHandlingAttributes(state, ' '));

        // In this situation the result is true - we are in a mode of
        // handling attributes, because we have previously read in
        // an attribute of "class"
        state.currentAttributeKey = "class";
        state.stringBuilder = new StringBuilder();
        assertTrue(HtmlParser.isHandlingAttributes(state, ' '));

        // Here, we observe that our stringbuilder has been collecting
        // characters - looks like this is possibly going to be "class"
        state.currentAttributeKey = "";
        state.stringBuilder = new StringBuilder("cla");
        assertTrue(HtmlParser.isHandlingAttributes(state, ' '));

        // In this situation, even though our prior state shows
        // no collected characters, we *are* reading an "a", meaning
        // we are no longer in a span of space characters between the
        // tag name and start of attributes.
        state.currentAttributeKey = "";
        state.stringBuilder = new StringBuilder();
        assertTrue(HtmlParser.isHandlingAttributes(state, 'a'));

        // various edge combinations.  Some are invalid situations
        // to be in, but since our False condition is so picky, we
        // don't need to throw an exception.
        // ********************

        state.currentAttributeKey = "class";
        state.stringBuilder = new StringBuilder();
        assertTrue(HtmlParser.isHandlingAttributes(state, 'a'));

        state.currentAttributeKey = "";
        state.stringBuilder = new StringBuilder("cla");
        assertTrue(HtmlParser.isHandlingAttributes(state, 'a'));

        state.currentAttributeKey = "class";
        state.stringBuilder = new StringBuilder("dat");
        assertTrue(HtmlParser.isHandlingAttributes(state, 'a'));

        state.currentAttributeKey = "class";
        state.stringBuilder = new StringBuilder("dat");
        assertTrue(HtmlParser.isHandlingAttributes(state, ' '));
    }

}

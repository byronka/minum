package com.renomad.minum.utils;

import com.renomad.minum.Context;
import com.renomad.minum.htmlparsing.*;
import com.renomad.minum.logging.TestLogger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;

public class HtmlParserTests {
    private static TestLogger logger;
    private static FileUtils fileUtils;
    private static Context context;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger) context.getLogger();
        fileUtils = context.getFileUtils();
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

    @Test
    public void test_HtmlParser_UnusualCases() {
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

    /*
        ********************
             EDGE CASES
        ********************

     */

    @Test
    public void test_HtmlParser_Edge_InvalidHtmlTag() {
        assertThrows(ParsingException.class, () -> new HtmlParser().parse("<foo></foo>"));
    }

    @Test
    public void test_HtmlParser_Edge_SpaceBeforeTagname() {
        var result = new HtmlParser().parse("<  p></p>");
        assertEquals(result, List.of(new HtmlParseNode(ParseNodeType.ELEMENT, new TagInfo(TagName.P, Map.of()), List.of(), "")));
    }

    @Test
    public void test_HtmlParser_Edge_InvalidClosingTag() {
        assertThrows(ParsingException.class, () -> new HtmlParser().parse("<p></br>"));
    }

    /**
     * invalid character after forward slash in start tag
     */
    @Test
    public void test_HtmlParser_Edge_InvalidChar() {
        assertThrows(ParsingException.class, () -> new HtmlParser().parse("<br//>"));
    }

    /**
     * invalid character after forward slash in start tag
     */
    @Test
    public void test_HtmlParser_Edge_InvalidChar2() {
        assertThrows(ParsingException.class, () -> new HtmlParser().parse("<br id=foo //>"));
    }

    @Test
    public void test_HtmlParser_Edge_LargerFile() {
        String htmlText = fileUtils.readTextFile("src/test/resources/templates/templatebenchmarks/expected_stock_output.html");
        List<HtmlParseNode> htmlRoots = new HtmlParser().parse(htmlText);
        List<List<String>> myList = htmlRoots.stream().map(x -> x.print()).filter(x -> ! x.isEmpty()).toList();
        var expected = fileUtils.readTextFile("src/test/resources/templates/templatebenchmarks/expected_stock_output_parsed.txt");
        assertEquals(myList.toString(), expected);
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
        assertEquals(htmlRoots.get(0).tagInfo().tagName(), TagName.BUTTON);
    }

}

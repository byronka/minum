package minum.utils;

import minum.Context;
import minum.htmlparsing.*;
import minum.testing.TestLogger;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static minum.testing.TestFramework.assertEquals;
import static minum.testing.TestFramework.assertThrows;
import static minum.utils.ThrowingRunnable.throwingRunnableWrapper;

public class HtmlParserTests {
    private final TestLogger logger;

    public HtmlParserTests(Context context) {
        this.logger = (TestLogger) context.getLogger();
        logger.testSuite("TheBrigTests");
    }

    public void tests() throws IOException {

        logger.test("detail work"); {
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

        logger.test("unusual cases"); {
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

        logger.test("this should easily work"); {
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

        logger.test("initial happy path MVP html parsing");
        {
            String input = "<!DOCTYPE html><p class=\"baz biz\" id=\"wut\" fee=fi>foo<h1></h1></p><p></p><br foo=bar />";
            String inputWithSingleTicks = "<!DOCTYPE html><p class='baz biz' id='wut' fee=fi>foo<h1></h1></p><p></p><br foo=bar />";
            var expected = List.of(
                    new HtmlParseNode(
                            ParseNodeType.ELEMENT,
                            new TagInfo(TagName.DOCTYPE, Map.of("html", "")),
                            List.of(),
                            ""),
                    new HtmlParseNode(
                            ParseNodeType.ELEMENT,
                            new TagInfo(TagName.P, Map.of("class", "baz biz", "id", "wut", "fee", "fi")),
                            List.of(new HtmlParseNode(
                                            ParseNodeType.CHARACTERS,
                                            new TagInfo(TagName.NULL, Map.of()),
                                            List.of(),
                                            "foo"),
                                    new HtmlParseNode(
                                            ParseNodeType.ELEMENT,
                                            new TagInfo(TagName.H1, Map.of()),
                                            List.of(),
                                            "")
                            ),
                            ""),
                    new HtmlParseNode(
                            ParseNodeType.ELEMENT,
                            new TagInfo(TagName.P, Map.of()),
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

        logger.test("Invalid HTML tag"); {
            assertThrows(ParsingException.class, () -> new HtmlParser().parse("<foo></foo>"));
        }

        logger.test("Invalid closing tag"); {
            assertThrows(ParsingException.class, () -> new HtmlParser().parse("<foo></bar>"));
        }

    }
}

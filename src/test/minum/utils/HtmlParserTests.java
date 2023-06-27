package minum.utils;

import minum.htmlparsing.*;
import minum.testing.StopWatch;
import minum.testing.TestLogger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static minum.testing.TestFramework.assertEquals;

public class HtmlParserTests {
    private final TestLogger logger;

    public HtmlParserTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) throws IOException {

        logger.test("detail work"); {
            String input = "<br foo=bar />";
            var expected = List.of(
                    new HtmlParseNode(
                            ParseNodeType.ELEMENT,
                            new TagInfo(TagName.BR, Map.of("foo", "bar")),
                            List.of(),
                            ""));
            List<HtmlParseNode> result = HtmlParser.parse(input);
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
            List<HtmlParseNode> result = HtmlParser.parse(input);
            assertEquals(expected, result);
        }

        logger.test("this should easily work"); {
            String input = "<title>Stock Prices</title>";
            var expected = List.of(
                    new HtmlParseNode(
                            ParseNodeType.ELEMENT,
                            new TagInfo(TagName.DOCTYPE, Map.of("html", "")),
                            List.of(),
                            ""));
            List<HtmlParseNode> result = HtmlParser.parse(input);
            assertEquals(expected, result);
        }

        /*
        Initial stab at html parsing, round 2
         */
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

            List<HtmlParseNode> nodes = null;
            List<HtmlParseNode> nodesWithSingleTicks = null;

            StopWatch stopWatch = new StopWatch().startTimer();
            for (int i = 0; i < 1000; i++) {
                nodes = HtmlParser.parse(input);
                nodesWithSingleTicks = HtmlParser.parse(inputWithSingleTicks);
            }
            long durationMillis = stopWatch.stopTimer();

            logger.testPrint("Duration in millis was " + durationMillis);
            assertEquals(expected, nodes);
            assertEquals(expected, nodesWithSingleTicks);
        }

        logger.test("explosions!!!"); {
            String input = FileUtils.readTemplate("templatebenchmarks/expected_stock_output.html");
            HtmlParser.parse(input);
        }

    }
}

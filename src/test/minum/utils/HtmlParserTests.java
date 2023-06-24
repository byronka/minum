package minum.utils;

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


        /*
        Initial stab at html parsing, round 2
         */
        logger.test("initial happy path MVP html parsing");
        {
            String input = "<p class=\"baz biz\" id=\"wut\" fee=fi>foo<h1></h1></p><p></p>";
            String inputWithSingleTicks = "<p class='baz biz' id='wut' fee=fi>foo<h1></h1></p><p></p>";
            var expected = List.of(
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

    }
}

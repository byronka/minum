package atqa.web;

import atqa.testing.TestLogger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static atqa.testing.TestFramework.assertEquals;

public class HtmlParserTests {
    private final TestLogger logger;

    public HtmlParserTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) throws IOException {


        /*
        Initial stab at html parsing, round 2
         */
        logger.test("initial happy path MVP html parsing"); {
            String input = "<p>foo<h1></h1></p><p></p>";
            var expected = List.of(
                    new HtmlParseNode(
                            ParseNodeType.ELEMENT,
                            new TagInfo(TagName.P, Map.of()),
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

            List<HtmlParseNode> nodes = HtmlParser.parse(input);

            assertEquals(expected, nodes);
        }

        /*
        TDD'ing an initial stab at html parsing
         */
//        logger.test("initial happy path MVP html parsing"); {
//            String input = "<p>foo <foo> foo</p>";
//            var expected = List.of(
//                    new HtmlParser.HtmlParseNode(
//                            HtmlParser.ParseNodeType.ELEMENT,
//                            new HtmlParser.TagInfo(HtmlParser.TagName.P, Map.of(), false),
//                            List.of(new HtmlParser.HtmlParseNode(CHARACTERS, null, List.of(), "foo <foo> foo")),
//                            ""));
//
//            List<HtmlParser.HtmlParseNode> node = HtmlParser.parse(input);
//
//            assertEquals(expected, node);
//        }

    }
}

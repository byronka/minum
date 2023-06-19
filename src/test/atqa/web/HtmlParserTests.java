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
        TDD'ing an initial stab at html parsing
         */
        logger.test("initial happy path MVP html parsing"); {
            String input = "<p></p>";
            var expected = List.of(
                    new HtmlParser.HtmlParseNode(
                            HtmlParser.ParseNodeType.ELEMENT,
                            new HtmlParser.TagInfo(HtmlParser.TagName.P, Map.of(), false),
                            List.of(),
                            ""));

            List<HtmlParser.HtmlParseNode> node = HtmlParser.parse(input);

            assertEquals(expected, node);
        }
    }
}

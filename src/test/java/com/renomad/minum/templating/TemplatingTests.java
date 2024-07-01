package com.renomad.minum.templating;

import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static com.renomad.minum.templating.TemplateProcessor.buildProcessor;
import static com.renomad.minum.testing.TestFramework.*;

public class TemplatingTests {

    private static FileUtils fileUtils;
    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void setUpClass() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger)context.getLogger();
        fileUtils = new FileUtils(logger, context.getConstants());
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    /**
     * testing out a template rendering machine
     */
    @Test
    public void test_Template_Basic() {
        String template = "Hello {{name}}, I'm {{animal}}.  Nice to meet you {{ name }}";
        var myMap = Map.of("name", "byron", "animal", "cat");
        TemplateProcessor tp = buildProcessor(template);

        String renderedTemplate = tp.renderTemplate(myMap);

        assertEquals(renderedTemplate, "Hello byron, I'm cat.  Nice to meet you byron");
    }


    /**
     * If the user specifies a key that doesn't get used,
     * throw an exception.  We prioritize correctness with
     * this system.
     */
    @Test
    public void test_Template_TooManyKeys() {
        String template = "Hello there byron";
        var myMap = Map.of("name", "byron", "animal", "cat");
        TemplateProcessor tp = buildProcessor(template);

        assertThrows(TemplateRenderException.class, "No corresponding key in template found for these keys: name, animal", () -> tp.renderTemplate(myMap));
    }


    /**
     * template rendering - missing keys
     */
    @Test
    public void test_Template_MissingKeys() {
        String template = "Hello {{name}}, I'm {{animal}} {{missing_key}}";
        var myMap = Map.of("name", "byron", "animal", "cat");
        TemplateProcessor tp = buildProcessor(template);

        assertThrows(TemplateRenderException.class, "Missing a value for key {missing_key}", () -> tp.renderTemplate(myMap));
    }

    /**
     * template rendering - whitespace around key
     *
     * <p>
     *     If there is whitespace around a key, like {{ foo }}, it
     *     should work similarly to when there is no whitespace, like {{foo}}
     * </p>
     *
     */
    @Test
    public void test_Template_Whitespace() {
        String template = "Hello {{ name }}, I'm {{animal}}";
        var myMap = Map.of("name", "byron", "animal", "cat");
        TemplateProcessor tp = buildProcessor(template);

        String renderedTemplate = tp.renderTemplate(myMap);

        assertEquals(renderedTemplate, "Hello byron, I'm cat");
    }

    /**
     * A bug that was found in some production code.
     * Renamed values to protect the guilty.
     * <br>
     * This *should* only require a key of "name" in the map, but
     * due to a bug in the code, I was getting this exception:
     * <pre>
     * {@code com.renomad.minum.templating.TemplateRenderException: Missing a value for key {foo}}
     * </pre>
     */
    @Test
    public void test_Template_Complex1() {
        String template = "{{ name }} foo }}";
        var tp = buildProcessor(template);
        Map<String, String> name = Map.of("name", "test1");

        String result = tp.renderTemplate(name);

        assertEquals(result, "test1 foo }}");
    }

    /**
     * What should happen if the brackets aren't closed?
     */
    @Test
    public void test_Template_EdgeCase_NoClosingBrackets() {
        var inputs = List.of("abcd{{ f", "abcd{{ fo", "abcd{{ foo", "abcd{{ foot", "{{ foo this is a longer piece of text", "{{ foo this is a longer \npiece of text");
        var expected = List.of("abcd{{ f", "abcd{{ fo", "abcd{{ foo", "abcd{{ foo...", "{{ foo thi...", "{{ foo thi...");
        var expectedLocation = List.of("line 1 and column 7","line 1 and column 8", "line 1 and column 9", "line 1 and column 10", "line 1 and column 36", "line 2 and column 13");
        for (int i = 0; i < inputs.size(); i++) {
            int finalI = i;
            var ex = assertThrows(TemplateParseException.class, () -> buildProcessor(inputs.get(finalI)));
            assertEquals(ex.getMessage(), "parsing failed for string starting with \""+expected.get(i)+"\" at " + expectedLocation.get(finalI));
        }

    }

    /**
     * template for a more realistic input
     */
    @Test
    public void test_Templating_Performance() {
        var individualStockProcessor = TemplateProcessor.buildProcessor(fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/individual_stock.html"));
        var stockPrices = TemplateProcessor.buildProcessor(fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/stock_prices.html"));
        String expectedFullOutput = fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/expected_stock_output.html");


        StopwatchUtils stopwatch = new StopwatchUtils().startTimer();
        // rendered 500,000 times in 15,764 millis, which is 31,717 templates per second.
        // currently set lower to speed up testing in ordinary case
        int renderingCount = 1;
        IntStream.range(0, renderingCount).boxed().parallel().forEach(renderTemplate(individualStockProcessor, stockPrices, expectedFullOutput));
        logger.logDebug(() -> String.format("processed %d templates in %d millis", renderingCount, stopwatch.stopTimer()));
    }

    private static Consumer<Integer> renderTemplate(TemplateProcessor individualStockProcessor, TemplateProcessor stockPrices, String expectedFullOutput) {
        return x -> {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < Stock.dummyItems().size(); i++) {
                Stock stock = Stock.dummyItems().get(i);
                String renderedIndividualStock = individualStockProcessor.renderTemplate(Map.of(
                        "class", i % 2 == 1 ? "even" : "odd", // the example I used start with odd, so ...
                        "index", String.valueOf(i + 1),
                        "symbol", stock.getSymbol(),
                        "url", stock.getUrl(),
                        "name", stock.getName(),
                        "price", String.valueOf(stock.getPrice()),
                        "is_negative_change", stock.getChange() < 0 ? " class=\"minus\"" : "",
                        "is_negative_ratio", stock.getRatio() < 0 ? " class=\"minus\"" : "",
                        "change", String.valueOf(stock.getChange()),
                        "ratio", String.valueOf(stock.getRatio())
                ));
                parts.add(renderedIndividualStock);
                if (i < Stock.dummyItems().size() - 1) parts.add("\n");
            }
            String result = stockPrices.renderTemplate(Map.of("individual_stocks", String.join("", parts)));
            assertEquals(expectedFullOutput, result);
        };
    }

    /**
     * When the template value is injected into the template, each newline
     * should be indented relative to the start.
     */
    @Test
    public void test_indentation() {
        TemplateProcessor templateProcessor = TemplateProcessor.buildProcessor("foo bar {{ baz }}");
        String expected = """
                foo bar test line 1
                        test line 2
                        test line 3
                """.stripTrailing();

        String result = templateProcessor.renderTemplate(Map.of("baz", "test line 1\ntest line 2\ntest line 3"));

        assertEquals(result, expected);
    }

}

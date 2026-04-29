package com.renomad.minum.templating;

import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.IFileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static com.renomad.minum.templating.TemplateProcessor.buildProcessor;
import static com.renomad.minum.testing.TestFramework.*;

public class TemplatingTests {

    private static IFileUtils fileUtils;
    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void setUpClass() {
        context = buildTestingContext("TemplatingTests");
        logger = (TestLogger)context.getLogger();
        fileUtils = new FileUtils(logger, context.getConstants());
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

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
        assertEquals(tp.getOriginalText(), "Hello {{name}}, I'm {{animal}}.  Nice to meet you {{ name }}");
    }

    /**
     * Demonstrates what is necessary to build out an inner template.  In this
     * example, we have a "ul" element representing the totality of our
     * outer template, and then we expect to build out a list of names which is
     * our inner template.
     * <br>
     * After construction of the TemplateProcessor instances,
     * we will first render out the internal template, then put that in the outer.
     */
    @Test
    public void test_Template_SimpleInnerTemplate() {
        // set up the templates
        var innerTemplate = TemplateProcessor.buildProcessor("<li>{{ name }}</li>");
        var outerTemplate = TemplateProcessor.buildProcessor("""
                <ul>
                    {{ inner_template_goes_here }}
                </ul>
                """);

        // render the inner template
        String renderedInnerTemplate = innerTemplate.renderTemplate(
                List.of(Map.of("name", "alice"), Map.of("name", "bob")));

        // merge that into the outer template, rendering the full final result
        String finalResult = outerTemplate.renderTemplate(Map.of("inner_template_goes_here", renderedInnerTemplate));

        assertEquals(finalResult, """
                <ul>
                    <li>alice</li>
                    <li>bob</li>
                </ul>
                """);
    }

    /**
     * test providing a list of maps, which will cause the template to render multiple times,
     * once for each map of data
     */
    @Test
    public void test_Template_Basic_Multiple() {
        String template = "Hello {{name}}, I'm {{animal}}.  Nice to meet you {{ name }}";
        var data = List.of(
                Map.of("name", "byron", "animal", "cat"),
                Map.of("name", "alice", "animal", "dog"),
                Map.of("name", "bob", "animal", "tuna")
        );
        TemplateProcessor tp = buildProcessor(template);

        String renderedTemplate = tp.renderTemplate(data, "\n");

        assertEquals(renderedTemplate, """
                Hello byron, I'm cat.  Nice to meet you byron
                Hello alice, I'm dog.  Nice to meet you alice
                Hello bob, I'm tuna.  Nice to meet you bob""");
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

        assertThrows(TemplateRenderException.class,
                "These keys in the data did not match anything in the template: [name, animal]",
                () -> tp.renderTemplate(myMap));
    }

    /**
     * template rendering - missing keys
     */
    @Test
    public void test_Template_MissingKeys() {
        String template = "Hello {{name}}, I'm {{animal}} {{missing_key}}";
        var myMap = Map.of("name", "byron", "animal", "cat");
        TemplateProcessor tp = buildProcessor(template);

        assertThrows(TemplateRenderException.class,
                "These keys in the template were not provided data: [missing_key]",
                () -> tp.renderTemplate(myMap));
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
     * A test for a large and complex template - has one template
     * nested in the other, looped.
     * <br>
     * <pre>
     * Current records, on a HP ProDesk 600 G1 using an Intel Core i5-4590 CPU at 3.30GHz running Windows 10
     * ------------------------------------------------------------------------------------------------------
     *
     *   5000 milliseconds for 500,000 templates, about 100k per second
     *
     * </pre>
     *
     */
    @Test
    public void test_Templating_LargeComplex_Performance() throws IOException {
        int warmupIterations = 5;
        int mainIterations = 5;

        // the expected result
        String expectedFullOutput = fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/expected_stock_output.html");

        // create all the processors we'll need
        TemplateProcessor individualStockProcessor = TemplateProcessor.buildProcessor(fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/individual_stock.html"));
        TemplateProcessor stockPrices = TemplateProcessor.buildProcessor(fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/stock_prices.html"));

        // define some stock data for testing
        List<Stock> stocks = Stock.dummyItems();

        // create reusable maps
        List<Map<String,String>> stockPricesList = new ArrayList<>();

        // fill the maps with data from stocks
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            HashMap<String, String> stockPricesMap = new HashMap<>();
            stockPricesMap.put("class", i % 2 == 1 ? "even" : "odd"); // the example I used start with odd, so ...
            stockPricesMap.put("index", String.valueOf(i + 1));
            stockPricesMap.put("symbol", stock.getSymbol());
            stockPricesMap.put("url", stock.getUrl());
            stockPricesMap.put("name", stock.getName());
            stockPricesMap.put("price", String.valueOf(stock.getPrice()));
            stockPricesMap.put("is_negative_change", stock.getChange() < 0 ? " class=\"minus\"" : "");
            stockPricesMap.put("is_negative_ratio", stock.getRatio() < 0 ? " class=\"minus\"" : "");
            stockPricesMap.put("change", String.valueOf(stock.getChange()));
            stockPricesMap.put("ratio", String.valueOf(stock.getRatio()));
            stockPricesList.add(stockPricesMap);
        }

        logger.logDebug(() -> "STARTING WARMUP");
        benchmark(warmupIterations, stockPrices, individualStockProcessor, expectedFullOutput, stockPricesList);

        logger.logDebug(() -> "STARTING MAIN");
        benchmark(mainIterations, stockPrices, individualStockProcessor, expectedFullOutput, stockPricesList);
        benchmark(mainIterations, stockPrices, individualStockProcessor, expectedFullOutput, stockPricesList);
        benchmark(mainIterations, stockPrices, individualStockProcessor, expectedFullOutput, stockPricesList);
    }

    private static void benchmark(int renderingCount, TemplateProcessor stockPrices, TemplateProcessor individualStockProcessor, String expectedFullOutput, List<Map<String, String>> stockPricesList) {
        StopwatchUtils stopwatch = new StopwatchUtils().startTimer();
        IntStream.range(0, renderingCount).boxed().parallel().forEach(x -> {
            String individualStockValuesRendered = individualStockProcessor.renderTemplate(stockPricesList);
            String resultantString = stockPrices.renderTemplate(Map.of("individual_stocks", individualStockValuesRendered));
            assertEquals(expectedFullOutput, resultantString);
        });
        logger.logDebug(() -> String.format("processed %d templates in %d millis", renderingCount, stopwatch.stopTimer()));
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

    /**
     * A TDD-style test to ensure the processor being thread-safe.
     * Tests concurrent rendering with multiple threads and different data
     * to verify that the TemplateProcessor correctly handles concurrent access
     * without data corruption or race conditions.
     */
    @Test
    public void test_Template_Multi_Thread() {
        TemplateProcessor templateProcessor = buildProcessor("Hello {{name}}");
        int threadCount = 10;
        var futures = new ArrayList<CompletableFuture<String>>();

        // Create multiple concurrent render tasks with different data
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            var future = CompletableFuture.supplyAsync(() ->
                    templateProcessor.renderTemplate(Map.of("name", "thread_" + threadNum))
            );
            futures.add(future);
        }

        // Verify all threads completed successfully with correct results
        for (int i = 0; i < threadCount; i++) {
            String result = futures.get(i).join();
            assertEquals(result, "Hello thread_" + i);

        }
    }

    @Test
    public void test_EdgeCase_NoDataProvided_alternate() {
        TemplateProcessor templateProcessor = TemplateProcessor.buildProcessor("I am {{ foo }}");
        assertThrows(TemplateRenderException.class,
                "These keys in the template were not provided data: [foo]",
                () -> templateProcessor.renderTemplate(List.of(Map.of())));
    }

    @Test
    public void test_EdgeCase_InconsistentMaps() {
        TemplateProcessor templateProcessor = TemplateProcessor.buildProcessor("I am {{ foo }}");
        assertThrows(TemplateRenderException.class,
                "In registered data, the maps were inconsistent on these keys: [bar, foo]",
                () -> templateProcessor.renderTemplate(List.of(Map.of("foo", "abc"), Map.of("bar", "def"))));
    }

    @Test
    public void test_EdgeCase_MapsAreAllEmpty() {
        TemplateProcessor templateProcessor = TemplateProcessor.buildProcessor("I am {{ foo }}");
        assertThrows(TemplateRenderException.class,
                "These keys in the template were not provided data: [foo]",
                () -> templateProcessor.renderTemplate(List.of(Map.of(), Map.of())));
    }

    @Test
    public void test_EdgeCase_MapsIsEmpty() {
        TemplateProcessor templateProcessor = TemplateProcessor.buildProcessor("I am {{ foo }}");
        assertThrows(TemplateRenderException.class,
                "These keys in the template were not provided data: [foo]",
                () -> templateProcessor.renderTemplate(Map.of()));
    }

}

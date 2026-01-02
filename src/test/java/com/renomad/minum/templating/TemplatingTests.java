package com.renomad.minum.templating;

import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

        String renderedTemplate = tp.renderTemplate(data);

        assertEquals(renderedTemplate, """
                Hello byron, I'm cat.  Nice to meet you byron
                Hello alice, I'm dog.  Nice to meet you alice
                Hello bob, I'm tuna.  Nice to meet you bob""");
    }

    @Test
    public void test_Template_Multiple_EdgeCase_MissingKeys() {
        String template = "Hello {{name}}, I'm {{animal}}.  Nice to meet you {{ name }}";
        var data = List.of(
                Map.of("name", "byron"),
                Map.of("name", "alice"),
                Map.of("name", "bob", "animal", "tuna")
        );
        TemplateProcessor tp = buildProcessor(template);

        var ex = assertThrows(TemplateRenderException.class, () -> tp.renderTemplate(data));
        assertEquals(ex.getMessage(), "In registered data, the maps were inconsistent on these keys: [animal]");
    }

    @Test
    public void test_Template_Multiple_EdgeCase_MissingKeys_2() {
        String template = "Hello {{name}}, I'm {{animal}}.  Nice to meet you {{ name }}, and my favorite color is {{ color }}";
        var data = List.of(
                Map.of("name", "byron"),
                Map.of("name", "alice"),
                Map.of("name", "bob")
        );
        TemplateProcessor tp = buildProcessor(template);

        var ex = assertThrows(TemplateRenderException.class, () -> tp.renderTemplate(data));
        assertEquals(ex.getMessage(), "These keys in the template were not provided data: [color, animal]");
    }

    /**
     * When keys are provided but there are no spots in the template for them
     */
    @Test
    public void test_Template_Multiple_EdgeCase_MissingKeys_3() {
        String template = "Hello world";
        var data = List.of(
                Map.of("foo", "byron"),
                Map.of("foo", "alice"),
                Map.of("foo", "bob")
        );
        TemplateProcessor tp = buildProcessor(template);

        var ex = assertThrows(TemplateRenderException.class, () -> tp.renderTemplate(data));
        assertEquals(ex.getMessage(), "These keys in the data did not match anything in the template: [foo]");
    }

    @Test
    public void test_Template_RenderingWithoutKeys() {
        String template = "Hello world";
        TemplateProcessor tp = buildProcessor(template);
        String result = tp.renderTemplate();
        assertEquals(result, "Hello world");
    }

    /**
     * If we register an inner template but there's no keys provided or needed
     */
    @Test
    public void test_Template_RenderingWithInnerTemplate_NoData() {
        String template = "Hello {{ inner_template }}";
        TemplateProcessor tp = buildProcessor(template);
        tp.registerInnerTemplate("inner_template", TemplateProcessor.buildProcessor("I am inner"));
        String result = tp.renderTemplate();
        assertEquals(result, "Hello I am inner");
    }


    /**
     * If we register an inner template but give the whole thing no data
     */
    @Test
    public void test_Template_EdgeCase_RenderingWithInnerTemplate_MissingData() {
        String template = "Hello {{ inner_template }}";
        TemplateProcessor tp = buildProcessor(template);
        tp.registerInnerTemplate("inner_template", TemplateProcessor.buildProcessor("I am {{ inner }}"));
        var exception = assertThrows(TemplateRenderException.class, () -> tp.renderTemplate());
        assertEquals(exception.getMessage(), "No data was provided for these keys: [inner]");
    }

    /**
     * If we register an inner template and provide data for the outermost template
     * and the middle nested template but not the most inner.
     */
    @Test
    public void test_Template_EdgeCase_RenderingWithInnerTemplate_MissingData_DeeperNesting() {
        String template = "Hello from the outer {{ name }}";
        TemplateProcessor tp = buildProcessor(template);
        tp.registerInnerTemplate("inner_template", TemplateProcessor.buildProcessor("I am middle {{ name }}, {{ giraffe }}"));
        tp.getInnerTemplate("inner_template").registerInnerTemplate("giraffe", TemplateProcessor.buildProcessor("my name is {{ name }}"));
        tp.getInnerTemplate("inner_template").registerData(List.of(Map.of("name", "foo")));
        var exception = assertThrows(TemplateRenderException.class, () -> tp.renderTemplate(Map.of("name", "bar")));
        assertEquals(exception.getMessage(), "No data was provided for these keys: [name]");
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

    @Test
    public void test_Template_Small_Performance() {
        int renderingCount = 10;
        TemplateProcessor templateProcessor = buildProcessor("Hello {{name}}");
        var data = Map.of("name", "world");
        StopwatchUtils stopwatch = new StopwatchUtils().startTimer();
        IntStream.range(0, renderingCount).boxed().parallel().forEach(x -> {
            assertEquals(templateProcessor.renderTemplate(data), "Hello world");
        });
        logger.logDebug(() -> String.format("processed %d templates in %d millis", renderingCount, stopwatch.stopTimer()));
    }

    @Test
    public void test_Template_Multi_Thread() {
        TemplateProcessor templateProcessor = buildProcessor("Hello {{name}}");
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> templateProcessor.renderTemplate(Map.of("name", "world")));
        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> templateProcessor.renderTemplate(Map.of("name", "future")));
        assertEquals(future.join(), "Hello world");
        assertEquals(future2.join(), "Hello future");
    }


    /**
     * A test for a large and complex template - has one template
     * nested in the other, looped.
     * <br>
     * <pre>
     * Current records, on a HP ProDesk 600 G1 using an Intel Core i5-4590 CPU at 3.30GHz running Windows 10
     * ------------------------------------------------------------------------------------------------------
     *
     *   1478 milliseconds for 500,000 templates, or 338,295 templates per second.
     *
     * </pre>
     *
     */
    @Test
    public void test_Templating_LargeComplex_Performance() {
        int warmupIterations = 5;
        int mainIterations = 5;

        // the expected result
        String expectedFullOutput = fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/expected_stock_output.html");

        // create all the processors we'll need
        TemplateProcessor individualStockProcessor = TemplateProcessor.buildProcessor(fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/individual_stock.html"));
        TemplateProcessor stockPrices = TemplateProcessor.buildProcessor(fileUtils.readTextFile("src/test/webapp/templates/templatebenchmarks/stock_prices.html"));

        // register internal templates
        stockPrices.registerInnerTemplate("individual_stocks", individualStockProcessor);

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
        benchmark(warmupIterations, stockPrices, expectedFullOutput, stockPricesList, true);
        benchmark(warmupIterations, stockPrices, expectedFullOutput, stockPricesList, false);

        logger.logDebug(() -> "STARTING MAIN");
        benchmark(mainIterations, stockPrices, expectedFullOutput, stockPricesList, true);
        benchmark(mainIterations, stockPrices, expectedFullOutput, stockPricesList, false);
        benchmark(mainIterations, stockPrices, expectedFullOutput, stockPricesList, true);
        benchmark(mainIterations, stockPrices, expectedFullOutput, stockPricesList, false);
        benchmark(mainIterations, stockPrices, expectedFullOutput, stockPricesList, true);
        benchmark(mainIterations, stockPrices, expectedFullOutput, stockPricesList, false);
    }

    private static void benchmark(int renderingCount, TemplateProcessor stockPrices, String expectedFullOutput, List<Map<String, String>> stockPricesList, boolean runWithChecks) {
        StopwatchUtils stopwatch = new StopwatchUtils().startTimer();
        IntStream.range(0, renderingCount).boxed().parallel().forEach(x -> {
            stockPrices.getInnerTemplate("individual_stocks").registerData(stockPricesList);
            String resultantString = stockPrices.renderTemplate(runWithChecks);
            assertEquals(expectedFullOutput, resultantString);
        });
        if (runWithChecks) {
            logger.logDebug(() -> String.format("with checks, processed %d templates in %d millis", renderingCount, stopwatch.stopTimer()));
        } else {
            logger.logDebug(() -> String.format("without checks, processed %d templates in %d millis", renderingCount, stopwatch.stopTimer()));
        }

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

    @Test
    public void test_EdgeCase_EmptyStringInput() {
        assertThrows(TemplateRenderException.class,
                "The input to building a template must be a non-empty string",
                () -> TemplateProcessor.buildProcessor(""));
        assertThrows(TemplateRenderException.class,
                "The input to building a template must be a non-empty string",
                () -> TemplateProcessor.buildProcessor(null));
        // it is (maybe surprising), however, allowed to provide a template that is just a *blank* string.
        TemplateProcessor.buildProcessor("   ");
    }

    @Test
    public void test_EdgeCase_NoValueProvidedBeforeRender() {
        TemplateProcessor foo = TemplateProcessor.buildProcessor("Here is {{ foo }}");
        assertThrows(TemplateRenderException.class,
                "No data was provided for these keys: [foo]",
                () -> foo.renderTemplate());
    }

    /**
     *
     * We will allow building templates with no keys, because only templates will
     *      have proper indentation added.
     */
    @Test
    public void test_EdgeCase_applyingInnerTemplate() {
        TemplateProcessor outer = TemplateProcessor.buildProcessor("Here is {{ inner }}\nand {{ inner }} is here too.");
        TemplateProcessor inner = TemplateProcessor.buildProcessor("I'm Henry the eighth I am I am\nHenry the eighth I am I am\nI am married to the woman next door");
        outer.registerInnerTemplate("inner", inner);
        String render = outer.renderTemplate();
        assertEquals("""
                Here is I'm Henry the eighth I am I am
                        Henry the eighth I am I am
                        I am married to the woman next door
                and I'm Henry the eighth I am I am
                    Henry the eighth I am I am
                    I am married to the woman next door is here too.""", render);
    }

    /**
     * We can register both templates and ordinary data with the same keys,
     * we must prevent that overlap!
     */
    @Test
    public void test_EdgeCase_Overlap() {
        TemplateProcessor outer = TemplateProcessor.buildProcessor("Here is {{ inner }}\nand {{ inner }} is here too.");
        TemplateProcessor inner = TemplateProcessor.buildProcessor("I'm Henry the eighth I am I am\nHenry the eighth I am I am\nI am married to the woman next door");
        outer.registerInnerTemplate("inner", inner);
        outer.registerData(List.of(Map.of("inner", "fubar")));
        assertThrows(TemplateRenderException.class,
                "These keys in the data did not match anything in the template: [inner]",
                outer::renderTemplate);
    }

    /**
     * Should get the error message with the .renderData() method too
     */
    @Test
    public void test_EdgeCase_Overlap_UsingRenderData() {
        TemplateProcessor outer = TemplateProcessor.buildProcessor("Here is {{ inner }}\nand {{ inner }} is here too.");
        TemplateProcessor inner = TemplateProcessor.buildProcessor("I'm Henry the eighth I am I am\nHenry the eighth I am I am\nI am married to the woman next door");
        outer.registerInnerTemplate("inner", inner);
        assertThrows(TemplateRenderException.class,
                "These keys in the data did not match anything in the template: [inner]",
                () -> outer.renderTemplate(Map.of("inner", "fubar")));
    }


    @Test
    public void test_EdgeCase_RegisteringInnerTemplateTwice() {
        TemplateProcessor outer = TemplateProcessor.buildProcessor("Here is {{ inner }}\nand {{ inner }} is here too.");
        TemplateProcessor inner = TemplateProcessor.buildProcessor("I'm Henry the eighth I am I am\nHenry the eighth I am I am\nI am married to the woman next door");
        outer.registerInnerTemplate("inner", inner);
        assertThrows(TemplateRenderException.class,
                "key is already registered for use in another template: inner",
                () -> outer.registerInnerTemplate("inner", inner));
    }

    @Test
    public void test_EdgeCase_CannotRegisterNullInnerTemplate() {
        TemplateProcessor inner = TemplateProcessor.buildProcessor("I am {{ foo }}");
        assertThrows(TemplateRenderException.class, "The template must not be null",
                () -> inner.registerInnerTemplate("inner", null));
        assertThrows(TemplateRenderException.class, "The key must be a valid non-blank string",
                () -> inner.registerInnerTemplate("   ", inner));
        assertThrows(TemplateRenderException.class, "The key must be a valid non-blank string",
                () -> inner.registerInnerTemplate("", inner));
        assertThrows(TemplateRenderException.class, "The key must be a valid non-blank string",
                () -> inner.registerInnerTemplate(null, inner));
    }

    /**
     * What happens if we register a template inside itself?
     */
    @Test
    public void test_EdgeCase_RecursiveTemplates() {
        TemplateProcessor inner = TemplateProcessor.buildProcessor("I am {{ foo }}");
        assertThrows(TemplateRenderException.class,
                "Disallowed to register a template to itself as an inner template",
                () -> inner.registerInnerTemplate("inner", inner));
    }

    @Test
    public void test_EdgeCase_NoDataProvided() {
        TemplateProcessor inner = TemplateProcessor.buildProcessor("I am {{ foo }}");
        assertThrows(TemplateRenderException.class, "No data provided in registerData call", () -> inner.registerData(List.of()));
        assertThrows(TemplateRenderException.class, "provided data cannot be null", () -> inner.registerData(null));
    }

    @Test
    public void test_EdgeCase_NoDataProvided_alternate() {
        TemplateProcessor inner = TemplateProcessor.buildProcessor("I am {{ foo }}");
        inner.registerData(List.of(Map.of()));
        assertThrows(TemplateRenderException.class, "These keys in the template were not provided data: [foo]", () -> inner.renderTemplate());
    }

    @Test
    public void test_EdgeCase_InconsistentMaps() {
        TemplateProcessor inner = TemplateProcessor.buildProcessor("I am {{ foo }}");
        inner.registerData(List.of(Map.of("foo", "abc"), Map.of("bar", "def")));
        assertThrows(TemplateRenderException.class,
                "In registered data, the maps were inconsistent on these keys: [bar, foo]",
                inner::renderTemplate);
    }

    @Test
    public void test_EdgeCase_MapsAreAllEmpty() {
        TemplateProcessor inner = TemplateProcessor.buildProcessor("I am {{ foo }}");
        inner.registerData(List.of(Map.of(), Map.of()));
        assertThrows(TemplateRenderException.class,
                "These keys in the template were not provided data: [foo]",
                inner::renderTemplate);
    }

    /**
     * If the user wants to render a template with no data, let them.
     */
    @Test
    public void test_EdgeCase_RenderingSimpleTemplateNoData() {
        var tp = TemplateProcessor.buildProcessor("I am foo");
        assertEquals("I am foo", tp.renderTemplate());
        assertEquals("I am foo", tp.renderTemplate(Map.of()));
    }

    /**
     * These are some example of weird templates and expected results
     */
    @Test
    public void test_EdgeCase_UnusualAndBadTemplates() {
        String result1 = TemplateProcessor.buildProcessor("hello {{ world }} }").renderTemplate(Map.of("world", "foo"));
        assertEquals(result1, "hello foo }");

        assertThrows(TemplateParseException.class,
                "parsing failed for string starting with \"{{ a }\" at line 1 and column 5",
                () -> TemplateProcessor.buildProcessor("{{ a }").renderTemplate(Map.of()));

        assertThrows(TemplateParseException.class,
                "parsing failed for string starting with \"{{ a } \" at line 1 and column 6",
                () -> TemplateProcessor.buildProcessor("{{ a } ").renderTemplate(Map.of()));

        String result2 = TemplateProcessor.buildProcessor("{").renderTemplate(Map.of());
        assertEquals(result2, "{");

        String result3 = TemplateProcessor.buildProcessor("{").renderTemplate();
        assertEquals(result3, "{");

        String result4 = TemplateProcessor.buildProcessor("hello {{ world }}").renderTemplate(Map.of("world", "foo"));
        assertEquals(result4, "hello foo");
    }

    /**
     * Trying a case of a template nested
     * two times.
     */
    @Test
    public void test_EdgeCase_DeeplyNested() {
        TemplateProcessor aTemplate = buildProcessor("A template. {{ b_template }}");
        TemplateProcessor bTemplate = buildProcessor("B template. {{ c_template }}");
        TemplateProcessor cTemplate = buildProcessor("C template.");

        aTemplate.registerInnerTemplate("b_template", bTemplate);
        TemplateProcessor bTemplate1 = aTemplate.getInnerTemplate("b_template");
        bTemplate1.registerInnerTemplate("c_template", cTemplate);
        TemplateProcessor cTemplate1 = bTemplate1.getInnerTemplate("c_template");

        assertEquals("A template. B template. C template.", aTemplate.renderTemplate());
        assertEquals("B template. C template.", bTemplate1.renderTemplate());
        assertEquals("C template.", cTemplate1.renderTemplate());
    }

    /**
     * A bit more involved than {@link #test_EdgeCase_DeeplyNested}, this one
     * has data.
     */
    @Test
    public void test_EdgeCase_DeeplyNested_withData() {
        TemplateProcessor aTemplate = buildProcessor("A template. {{ key1 }} {{ key2 }} {{ b_template }}");
        TemplateProcessor bTemplate = buildProcessor("B template.  {{ key1 }} {{ key2 }} {{ c_template }}");
        TemplateProcessor cTemplate = buildProcessor("C template.  {{ key1 }} {{ key2 }}");

        List<Map<String, String>> data = List.of(Map.of("key1", "foo",
                "key2", "bar"));

        var newBTemplate = aTemplate.registerInnerTemplate("b_template", bTemplate);
        var newCTemplate = newBTemplate.registerInnerTemplate("c_template", cTemplate);
        aTemplate.registerData(data);
        newBTemplate.registerData(data);
        newCTemplate.registerData(data);

        assertEquals("A template. foo bar B template.  foo bar C template.  foo bar", aTemplate.renderTemplate());
        assertEquals("B template.  foo bar C template.  foo bar", newBTemplate.renderTemplate());
        assertEquals("C template.  foo bar", newCTemplate.renderTemplate());
        assertEquals("A template. {{ key1 }} {{ key2 }} {{ b_template }}", aTemplate.getOriginalText());
        assertEquals("B template.  {{ key1 }} {{ key2 }} {{ c_template }}", newBTemplate.getOriginalText());
        assertEquals("C template.  {{ key1 }} {{ key2 }}", newCTemplate.getOriginalText());
    }

}

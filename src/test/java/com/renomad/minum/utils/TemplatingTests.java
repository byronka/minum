package com.renomad.minum.utils;

import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.templating.TemplateRenderException;
import com.renomad.minum.logging.TestLogger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static com.renomad.minum.templating.TemplateProcessor.buildProcessor;
import static com.renomad.minum.testing.TestFramework.*;

public class TemplatingTests {

    private static FileUtils fileUtils;

    @BeforeClass
    public static void setUpClass() {
        var context = buildTestingContext("unit_tests");
        var logger = (TestLogger)context.getLogger();
        fileUtils = new FileUtils(logger, context.getConstants());
    }

    /**
     * testing out a template rendering machine
     */
    @Test
    public void test_Template_Basic() {
        String template = "Hello {{name}}, I'm {{animal}}";
        var myMap = Map.of("name", "byron", "animal", "cat");
        TemplateProcessor tp = buildProcessor(template);

        String renderedTemplate = tp.renderTemplate(myMap);

        assertEquals(renderedTemplate, "Hello byron, I'm cat");
    }


    /**
     * template rendering - no keys
     */
    @Test
    public void test_Template_NoKeys() {
        String template = "Hello there byron";
        var myMap = Map.of("name", "byron", "animal", "cat");
        TemplateProcessor tp = buildProcessor(template);

        String renderedTemplate = tp.renderTemplate(myMap);

        assertEquals(renderedTemplate, "Hello there byron");
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

    /*
    This test is based on a benchmark comparison project I found at https://github.com/mbosecke/template-benchmark

    It's all about stocks.  It uses ordinary-sized text.

    The results: pretty stable at 27,000 templates rendered per second.

    For comparison, the referenced project shows this for other well-known Java template systems:

    Benchmark   Score(renderings / sec)
    Freemarker  15370
    Handlebars  17779
    Mustache    22164
    Pebble      32530
    Rocker      39739
    Thymeleaf   1084
    Trimou      22787
    Velocity    20835

    Notes:
    Pebble: pebble has 229 classes and 16,876 lines of core non-test code, not include its required dependencies
    Rocker: rocker has a very large and disparate code base, but looking at just java, it has 112 classes and 11,996 lines of core non-test code


    My templating code is 69 lines of code.
     */


    /**
     * template for a more realistic input
     */
    @Test
    public void test_Template_Realistic() {
        var individualStockProcessor = TemplateProcessor.buildProcessor(fileUtils.readTextFile("src/test/resources/templates/templatebenchmarks/individual_stock.html"));
        var stockPrices = TemplateProcessor.buildProcessor(fileUtils.readTextFile("src/test/resources/templates/templatebenchmarks/stock_prices.html"));
        var expectedOutput = fileUtils.readTextFile("src/test/resources/templates/templatebenchmarks/expected_stock_output.html");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Stock.dummyItems().size(); i++) {
            Stock stock = Stock.dummyItems().get(i);
            String renderedIndividualStock = individualStockProcessor.renderTemplate(Map.of(
                    "class", i % 2 == 0 ? "even" : "odd",
                    "index", String.valueOf(i + 1),
                    "symbol", stock.getSymbol(),
                    "url", stock.getUrl(),
                    "name", stock.getName(),
                    "price", String.valueOf(stock.getPrice()),
                    "is_minus", stock.getPrice() < 0 ? " class=\"minus\"" : "",
                    "change", String.valueOf(stock.getChange()),
                    "ratio", String.valueOf(stock.getRatio())
            ));
            sb.append(renderedIndividualStock);
        }
        String result = stockPrices.renderTemplate(Map.of("individual_stocks", sb.toString()));
    }
}

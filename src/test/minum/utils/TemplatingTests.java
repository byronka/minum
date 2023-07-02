package minum.utils;

import minum.TestContext;
import minum.templating.TemplateProcessor;
import minum.templating.TemplateRenderException;
import minum.testing.TestLogger;

import java.util.Map;

import static minum.testing.TestFramework.assertEquals;
import static minum.testing.TestFramework.assertThrows;
import static minum.templating.TemplateProcessor.buildProcessor;

public class TemplatingTests {
    private final TestLogger logger;

    public TemplatingTests(TestContext context) {
        this.logger = context.getLogger();
        logger.testSuite("Templating Tests", "TemplatingTests");
    }

    public void tests() {

        /*
        This is a program that generates a list of objects which, when we loop
        through, we can use it to quickly render a template.
         */
        logger.test("testing out a template rendering machine"); {
            String template = "Hello {{name}}, I'm {{animal}}";
            var myMap = Map.of("name", "byron", "animal", "cat");
            TemplateProcessor tp = buildProcessor(template);

            String renderedTemplate = tp.renderTemplate(myMap);

            assertEquals(renderedTemplate, "Hello byron, I'm cat");
        }

        logger.test("template rendering - no keys"); {
            String template = "Hello there byron";
            var myMap = Map.of("name", "byron", "animal", "cat");
            TemplateProcessor tp = buildProcessor(template);

            String renderedTemplate = tp.renderTemplate(myMap);

            assertEquals(renderedTemplate, "Hello there byron");
        }

        logger.test("template rendering - missing keys"); {
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
        Mustache: mentioned as really simple. 124 classes and 11,346 lines of
                  code.  " I always liked Mustache because of its simplicity -
                  it’s "just enough" templating - and you really couldn’t wish
                  for a cleaner, leaner, more lightweight library than this one,
                  if you have to render templates in the JVM"
                  - https://spring.io/blog/2016/11/21/the-joy-of-mustache-server-side-templates-for-the-jvm

        Pebble: pebble has 229 classes and 16,876 lines of core non-test code, not include its required dependencies
        Rocker: rocker has a very large and disparate code base, but looking at just java, it has 112 classes and 11,996 lines of core non-test code


        My templating code is 69 lines of code.
         */
        logger.test("template for a more realistic input");
        {
            var individualStockProcessor = TemplateProcessor.buildProcessor(FileUtils.readTemplate("templatebenchmarks/individual_stock.html"));
            var stockPrices = TemplateProcessor.buildProcessor(FileUtils.readTemplate("templatebenchmarks/stock_prices.html"));
            var expectedOutput = FileUtils.readTemplate("templatebenchmarks/expected_stock_output.html");

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
}

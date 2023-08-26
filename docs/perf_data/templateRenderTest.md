Templating Speed Test
=====================

This test is based on a benchmark comparison project I found at https://github.com/mbosecke/template-benchmark

It's all about stocks.  It uses ordinary-sized text.

The results: pretty stable at 27,000 templates rendered per second.

For comparison, the referenced project shows this for other well-known Java template systems:

| Benchmark  | Score(renderings / sec) |
|------------|-------------------------|
| Minum      | 27,000                  |
| Freemarker | 15,370                  |
| Handlebars | 17,779                  |
| Mustache   | 22,164                  |
| Pebble     | 32,530                  |
| Rocker     | 39,739                  |
| Thymeleaf  | 1,084                   |
| Trimou     | 22,787                  |
| Velocity   | 20,835                  |

Notes:
------

* Pebble: pebble has 229 classes and 16,876 lines of core non-test code, not include its required dependencies
* Rocker: rocker has a very large and disparate code base, but looking at just java, it has 112 classes and 11,996 lines of core non-test code


```java

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
```
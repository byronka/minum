/**
 * Converts HTML text into a Java data structure.  It processes quickly, and can provide
 * an ability to search for HTML elements by attributes.
 * <p>
 *     Here is an example of a test exercising the parser:
 * </p>
 * <pre>{@code
 *     @Test
 *     public void test_HtmlParser_Details1() {
 *         String input = "<br foo=bar />";
 *         var expected = List.of(
 *                 new HtmlParseNode(
 *                         ParseNodeType.ELEMENT,
 *                         new TagInfo(TagName.BR, Map.of("foo", "bar")),
 *                         List.of(),
 *                         ""));
 *         List<HtmlParseNode> result = new HtmlParser().parse(input);
 *         assertEquals(expected, result);
 *     }
 * }</pre>
 * <p>
 *     Some of the testing library depends on this framework, such
 *     as {@link com.renomad.minum.web.FunctionalTesting.TestResponse#searchOne(com.renomad.minum.htmlparsing.TagName, java.util.Map)}.
 *     This is heavily used in the tests on Minum, as well as being available to applications, such as
 *     this example from the Memoria project:
 * </p>
 * <pre>{@code
 *        logger.test("GET the detail view of a person");
 *         {
 *             var response = ft.get("person?id=" + personId);
 *             assertEquals(response.searchOne(TagName.H2, Map.of("class","lifespan-name")).innerText().trim(), "John Doe");
 *             assertEquals(response.searchOne(TagName.SPAN, Map.of("class","lifespan-era")).innerText().trim(), "November 14, 1917 to March 19, 2003");
 *         }
 * }</pre>
 *
 */
package com.renomad.minum.htmlparsing;
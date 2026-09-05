package com.renomad.minum.htmlparsing;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * Deliberately-failing regression tests for defects found in the
 * {@code com.renomad.minum.htmlparsing} package.
 * <p>
 *     Each test in this class asserts the <em>correct</em> (desired) behavior,
 *     and therefore fails against the current code.
 * </p>
 */
public class FindingsHtmlParsingTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = TestFramework.buildTestingContext("FindingsHtmlParsingTests");
        logger = (TestLogger) context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        TestFramework.shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    /**
     * FINDING 11: the script close-tag match is case sensitive and whitespace intolerant,
     * so the rest of the document is silently swallowed.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/htmlparsing/HtmlParser.java:275} and
     *     {@code :282} - {@code scriptElement} is the literal lowercase character list
     *     {@code </script>} and {@code determineScriptState} looks for exactly that sequence in
     *     the ring buffer.  Opening tags, by contrast, are matched case-insensitively, so
     *     {@code <SCRIPT>} does put the parser into script mode.  The result is that
     *     {@code </SCRIPT>}, or {@code </script >} (a space before the angle bracket, which HTML
     *     permits), never ends script mode.  The parser then consumes the entire remainder of
     *     the document as script text and returns an empty node list - with no exception and no
     *     diagnostic.  Anything that relies on this parser to inspect a document (the
     *     framework's own functional-testing helpers, or any application scanning HTML) is
     *     silently handed nothing.
     * </p>
     * <p>
     *     Correct behavior: the closing script tag must be recognised case-insensitively and
     *     must tolerate whitespace before the closing angle bracket, exactly as the opening tag
     *     is.  In each of the three documents below the trailing {@code <p>important</p>} must
     *     still be parsed.
     * </p>
     */
    @Test
    public void test_Finding_ScriptCloseTagIsCaseInsensitive() {
        var htmlParser = new HtmlParser();

        // control: the all-lowercase, whitespace-free form works today
        List<HtmlParseNode> control = htmlParser.parse("<script>var x = 1;</script><p>important</p>");
        assertEquals(control.size(), 2);

        assertParagraphIsFound(htmlParser, "<SCRIPT>var x = 1;</SCRIPT><p>important</p>",
                "an uppercase <SCRIPT> ... </SCRIPT> block");
        assertParagraphIsFound(htmlParser, "<script>var x = 1;</SCRIPT><p>important</p>",
                "a lowercase <script> closed by an uppercase </SCRIPT>");
        assertParagraphIsFound(htmlParser, "<script>var x = 1;</script ><p>important</p>",
                "a closing </script > with a space before the angle bracket");
    }

    /**
     * FINDING 12: deeply nested HTML overflows the stack.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/htmlparsing/HtmlParseNode.java} -
     *     {@code search} (via {@code recursiveTreeWalkSearch}), {@code print} and
     *     {@code toString} all recurse once per level of nesting with no depth limit.  The
     *     parser itself is iterative and happily accepts the document; only the traversal blows
     *     up.  A document of 20,000 nested elements is about 140,000 characters - far below the
     *     parser's own {@code MAX_HTML_SIZE} of 2,097,152 characters - and yet
     *     {@code search()} throws {@link StackOverflowError} at the default JVM stack size.
     *     A {@link StackOverflowError} is an {@link Error}, so it unwinds past the usual
     *     exception handling; when the HTML being parsed came from a remote source, this is a
     *     cheap denial of service.
     * </p>
     * <p>
     *     Correct behavior: either the traversal is iterative (or depth-limited), or the parser
     *     rejects documents nested more deeply than it can traverse.  Either way, a document
     *     the parser accepted must be safe to search.
     * </p>
     */
    @Test
    public void test_Finding_DeeplyNestedHtmlDoesNotOverflowStack() {
        final int depth = 20_000;
        String deeplyNestedHtml = "<b>".repeat(depth) + "hi" + "</b>".repeat(depth);
        // well under HtmlParser.MAX_HTML_SIZE (2,097,152)
        assertTrue(deeplyNestedHtml.length() < HtmlParser.MAX_HTML_SIZE,
                "this test document must be within the size the parser accepts");

        var htmlParser = new HtmlParser();
        List<HtmlParseNode> nodes = htmlParser.parse(deeplyNestedHtml);
        assertEquals(nodes.size(), 1);

        List<HtmlParseNode> found;
        try {
            found = nodes.getFirst().search(TagName.A, Map.of());
        } catch (StackOverflowError ex) {
            throw new AssertionError("HtmlParseNode.search should complete on a document the parser accepted.  " +
                    "A " + deeplyNestedHtml.length() + "-character document nested " + depth + " levels deep " +
                    "(well under MAX_HTML_SIZE of " + HtmlParser.MAX_HTML_SIZE + ") caused a StackOverflowError, " +
                    "because recursiveTreeWalkSearch recurses once per nesting level with no depth limit.");
        }

        assertTrue(found.isEmpty(),
                "there are no anchor tags in this document, so search should return an empty list");
    }

    private static void assertParagraphIsFound(HtmlParser htmlParser, String html, String descriptionOfHtml) {
        List<HtmlParseNode> nodes = htmlParser.parse(html);
        List<HtmlParseNode> paragraphs = new ArrayList<>();
        for (HtmlParseNode node : nodes) {
            paragraphs.addAll(node.search(TagName.P, Map.of()));
        }
        assertTrue(paragraphs.size() == 1,
                "the <p>important</p> that follows " + descriptionOfHtml + " must still be parsed, but the parser " +
                        "returned " + nodes.size() + " top-level node(s) and " + paragraphs.size() + " paragraph(s) - " +
                        "the closing script tag was not recognised, so the rest of the document was swallowed as " +
                        "script text");
        assertEquals(paragraphs.getFirst().innerText(), "important");
    }
}

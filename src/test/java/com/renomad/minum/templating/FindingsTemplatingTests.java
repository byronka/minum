package com.renomad.minum.templating;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.templating.TemplateProcessor.buildProcessor;
import static com.renomad.minum.testing.TestFramework.*;

/**
 * Deliberately-failing regression tests for defects found in the
 * {@code com.renomad.minum.templating} package.
 * <p>
 *     Each test in this class asserts the <em>correct</em> (desired) behavior,
 *     and therefore fails against the current code.
 * </p>
 */
public class FindingsTemplatingTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("FindingsTemplatingTests");
        logger = (TestLogger) context.getLogger();
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
     * FINDING 8: the output-buffer capacity calculation overflows a 32-bit int.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/templating/TemplateProcessor.java:117}
     *     - {@code int capacity = estimatedSizeOfSingleTemplate * data.size();}.  Both operands
     *     are {@code int}, so the multiplication wraps.  A template of roughly 30,000 characters
     *     rendered over 100,000 rows gives an estimate of about 3.3 billion, which wraps to a
     *     negative number, and {@code new StringBuilder(capacity)} then throws a raw
     *     {@link NegativeArraySizeException} straight out of the JDK.  Nothing about the message
     *     tells the operator what went wrong, and the exception type is not one any caller would
     *     think to catch.
     * </p>
     * <p>
     *     Correct behavior: the capacity is only an optimisation hint.  It must be computed in a
     *     way that cannot overflow (compute in {@code long} and clamp), so that rendering either
     *     succeeds or, if the result genuinely does not fit in memory, fails with one of the
     *     framework's own exceptions - never with {@link NegativeArraySizeException}.
     * </p>
     */
    @Test
    public void test_Finding_TemplateCapacityIntegerOverflow() {
        // a template of about 30,000 characters, with one templated key
        String template = "x".repeat(30_000) + "{{ name }}";
        TemplateProcessor processor = buildProcessor(template);

        int rowCount = 100_000;
        List<Map<String, String>> data = new ArrayList<>(rowCount);
        Map<String, String> row = Map.of("name", "a");
        for (int i = 0; i < rowCount; i++) {
            data.add(row);
        }

        Throwable thrown = null;
        try {
            processor.renderTemplate(data, "");
        } catch (Throwable ex) {
            thrown = ex;
        }

        assertFalse(thrown instanceof NegativeArraySizeException,
                "renderTemplate should never fail with NegativeArraySizeException.  The buffer-capacity hint " +
                        "(estimatedSizeOfSingleTemplate * data.size()) silently overflowed a 32-bit int for a " +
                        template.length() + "-character template over " + rowCount + " rows, and the negative value " +
                        "was handed straight to new StringBuilder(int).  Capacity must be computed as a long and " +
                        "clamped.  Actual failure: " + thrown);
    }

    /**
     * FINDING 9: rendering an empty list of rows throws NoSuchElementException.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/templating/TemplateProcessor.java:264}
     *     - {@code correctnessCheck} begins with {@code dataList.getFirst()}, with no guard for
     *     an empty list.  Rendering zero rows is an entirely ordinary situation (a table of
     *     search results with no matches, a list of a user's items before they have created any),
     *     and it blows up with a bare {@link java.util.NoSuchElementException} instead of
     *     producing empty output.
     * </p>
     * <p>
     *     Correct behavior: rendering an empty data list must produce the empty string.
     * </p>
     */
    @Test
    public void test_Finding_TemplateEmptyDataListThrows() {
        TemplateProcessor processor = buildProcessor("<li>{{ name }}</li>");

        String result;
        try {
            result = processor.renderTemplate(List.of(), "");
        } catch (RuntimeException ex) {
            throw new AssertionError("renderTemplate with an empty data list should render the empty string - " +
                    "rendering zero rows is normal (an empty search result, a user with no items yet).  Instead it " +
                    "threw " + ex, ex);
        }

        assertEquals(result, "");
    }

    /**
     * FINDING 10: a null value in the data map produces a raw NullPointerException.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/templating/TemplateSection.java:64-65}.
     *     {@code render} does {@code String value = myMap.get(key);} and passes the result
     *     straight into {@code tokenizer}, which dereferences it.  A {@link HashMap} happily
     *     stores a null value, so a caller who builds their data map from, say, a database row
     *     with a null column gets a {@link NullPointerException} from inside the templating
     *     engine with nothing to say which key was at fault.  Note that
     *     {@code correctnessCheck} does not catch this: it compares key <em>sets</em> only.
     * </p>
     * <p>
     *     Correct behavior: a null value must be reported as a
     *     {@link TemplateRenderException} whose message names the offending key, in keeping
     *     with the other diagnostics that class already produces.
     * </p>
     */
    @Test
    public void test_Finding_TemplateNullValueThrowsFrameworkException() {
        TemplateProcessor processor = buildProcessor("Hello {{ name }}!");

        // a HashMap permits null values (unlike Map.of)
        Map<String, String> myMap = new HashMap<>();
        myMap.put("name", null);

        var ex = assertThrows(TemplateRenderException.class, () -> processor.renderTemplate(myMap));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("name"),
                "the TemplateRenderException message should name the key whose value was null (\"name\").  " +
                        "Message was: " + ex.getMessage());
    }
}

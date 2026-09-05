package com.renomad.minum.state;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.TestFramework;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.List;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * Deliberately-failing regression tests for defects found in the
 * {@code com.renomad.minum.state} package.
 * <p>
 *     Each test in this class asserts the <em>correct</em> (desired) behavior,
 *     and therefore fails against the current code.
 * </p>
 */
public class FindingsConstantsTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = TestFramework.buildTestingContext("FindingsConstantsTests");
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
     * FINDING 13: {@code extractList} of an empty string returns a list containing one empty
     * string, which makes an empty SUSPICIOUS_PATHS setting ban every visitor to the homepage.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/state/Constants.java:270-280}.  When
     *     the property has a value, {@code extractList} goes straight to
     *     {@code propValue.trim().split("\\s*,\\s*")}.  {@code "".split(...)} returns
     *     {@code [""]} - an array of one empty string - so an explicitly-empty property such as
     *     {@code SUSPICIOUS_PATHS=} in minum.config yields {@code List.of("")} instead of an
     *     empty list.  The null branch immediately above gets this right (it checks
     *     {@code propDefault.isBlank()} and returns {@code List.of()}), so the two branches
     *     disagree.
     *     <br>
     *     The consequence is severe: {@code WebFramework} compares each request's isolated path
     *     against SUSPICIOUS_PATHS, and the isolated path of a request for the homepage
     *     ({@code GET / HTTP/1.1}) is itself the empty string.  So a configuration file that
     *     merely lists the property with no value causes every visitor to the homepage to be
     *     flagged as an attacker and put in the brig.  The same reasoning applies to the other
     *     list-valued properties (EXTRA_MIME_MAPPINGS, HOST_NAME lists, and so on).
     * </p>
     * <p>
     *     Correct behavior: an empty or blank property value must produce an empty list.
     * </p>
     * <p>
     *     <strong>CONFLICT - please read.</strong>  The existing test
     *     {@code src/test/java/com/renomad/minum/state/ConstantsTests.java:69} pins the current,
     *     buggy behavior with {@code assertEquals(Constants.extractList("", ""), List.of(""));}.
     *     That assertion is wrong and must be changed to {@code List.of()} as part of fixing
     *     this defect.  It has deliberately not been modified here.
     * </p>
     */
    @Test
    public void test_Finding_ExtractListOfEmptyStringIsEmpty() {
        List<String> result = Constants.extractList("", "");
        assertEquals(result, List.of(),
                "extractList of an empty property value must produce an empty list, not a list containing one " +
                        "empty string.  It returned a list of size " + result.size() + " whose contents are " +
                        describe(result) + ".  Because the isolated path of a request for the homepage is also the " +
                        "empty string, an empty SUSPICIOUS_PATHS setting would otherwise ban every visitor to the " +
                        "homepage.");

        List<String> blankResult = Constants.extractList("   ", "");
        assertEquals(blankResult, List.of(),
                "extractList of a blank property value must also produce an empty list.  It returned a list of size " +
                        blankResult.size() + " whose contents are " + describe(blankResult));
    }

    /**
     * Renders a list with each element quoted, so that a list containing the
     * empty string is distinguishable from an empty list in a failure message.
     */
    private static String describe(List<String> list) {
        var stringBuilder = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append('"').append(list.get(i)).append('"');
        }
        return stringBuilder.append("]").toString();
    }
}

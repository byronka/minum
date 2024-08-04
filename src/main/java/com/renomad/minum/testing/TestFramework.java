package com.renomad.minum.testing;

import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.queue.ActionQueueKiller;
import com.renomad.minum.utils.ThrowingRunnable;
import com.renomad.minum.web.FullSystem;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;

/**
 * These are utility functions for basic automated
 * testing.  It turns out you don't really need fancy tools
 * to do excellent testing.  Just a committment to
 * quality.  Don't let anyone tell you differently.
 */
public final class TestFramework {

    private TestFramework() {
        // making this private to be clearer it isn't supposed to be instantiated.
    }

    /**
     * assert that a particular chunk of code throws a particular
     * exception.
     * <p>
     *     Example usage:
     * </p>
     * <pre>
     *     <code>
     *         {@code assertThrows(TemplateRenderException.class, "Missing a value for key {missing_key}", () -> tp.renderTemplate(myMap));}
     *     </code>
     * </pre>
     */
    public static <T> T assertThrows(Class<T> myEx, ThrowingRunnable r) {
        return assertThrows(myEx, null, r);
    }

    // quick note about the warning suppression - we already checked that the
    // case will be valid, when we checked if (!myEx.isInstance(ex)).
    @SuppressWarnings("unchecked")
    public static <T> T assertThrows(Class<T> myEx, String expectedMsg, ThrowingRunnable r) {
        try {
            r.run();
            throw new TestFailureException("Failed to throw exception");
        } catch (Exception ex) {
            if (!myEx.getTypeName().equals(ex.getClass().getTypeName())) {
                String msg = String.format("This did not throw the expected exception type (%s).  Instead, (%s) was thrown", myEx, ex);
                throw new TestFailureException(msg);
            }
            if (expectedMsg != null && !ex.getMessage().equals(expectedMsg)) {
                String msg = String.format("Did not get expected message (%s). Instead, got: %s", expectedMsg, ex.getMessage());
                throw new TestFailureException(msg);
            }
            return (T) ex;
        }

    }

    /**
     * A helper for testing - assert two generics are equal.  If you
     * need to compare two byte arrays, see {@link #assertEqualByteArray(byte[], byte[])}
     */
    public static <T> void assertEquals(T left, T right) {
        if (! left.equals(right)) {
            throw new TestFailureException("Not equal! %nleft:  %s %nright: %s".formatted(showWhiteSpace(left.toString()), showWhiteSpace(right.toString())));
        }
    }

    /**
     * Compares two byte arrays for equality
     */
    public static void assertEqualByteArray(byte[] left, byte[] right) {
        if (left == null || right == null) throw new TestFailureException("at least one of the inputs was null: left: %s right: %s".formatted(Arrays.toString(left), Arrays.toString(right)));
        if (left.length != right.length) throw new TestFailureException("Not equal! left length: %d right length: %d".formatted(left.length, right.length));
        for (int i = 0; i < left.length; i++) {
            if (left[i] != right[i]) throw new TestFailureException("Not equal! at index %d left was: %d right was: %d".formatted(i, left[i], right[i]));
        }
    }

    public static void assertEqualByteArray(byte[] left, byte[] right, String failureMessage) {
        try {
            assertEqualByteArray(left, right);
        } catch (TestFailureException ex) {
            throw new TestFailureException(ex.getMessage() + ". " + failureMessage);
        }
    }

    /**
     * asserts two lists are equal, ignoring the order.
     * For example, (a, b) is equal to (b, a)
     * <p>
     * Note that the lists must be of comparable objects, or else
     * a ClassCastException will be thrown
     */
    public static void assertEqualsDisregardOrder(List<? extends CharSequence> left, List<? extends CharSequence> right) {
        if (left.size() != right.size()) {
            throw new TestFailureException(String.format("different sizes: left was %d, right was %d%n", left.size(), right.size()));
        }
        List<? extends CharSequence> orderedLeft = left.stream().sorted().toList();
        List<? extends CharSequence> orderedRight = right.stream().sorted().toList();

        for (int i = 0; i < left.size(); i++) {
            if (!orderedLeft.get(i).toString().contentEquals(orderedRight.get(i))) {
                throw new TestFailureException(
                        String.format(
                                "%n%ndifferent values:%n%nleft:  %s%nright: %s%n%nfull left:%n-----------%n%s%n%nfull right:%n-----------%n%s%n",
                                orderedLeft.get(i),
                                orderedRight.get(i),
                                String.join("\n", showWhiteSpace(left.toString())),
                                String.join("\n", showWhiteSpace(right.toString()))));
            }
        }
    }

    public static void assertEqualsDisregardOrder(List<? extends CharSequence> left, List<? extends CharSequence> right, String failureMessage) {
        try {
            assertEqualsDisregardOrder(left, right);
        } catch (TestFailureException ex) {
            throw new TestFailureException(ex.getMessage() + ". " + failureMessage);
        }
    }

    /**
     * asserts that two lists are equal in value and order.
     * <br><br>
     * For example, (a, b) is equal to (a, b)
     * Does not expect null as an input value.
     * Two empty lists are considered equal.
     */
    public static <T> void assertEquals(List<T> left, List<T> right) {
       assertEquals(left, right, "");
    }

    /**
     * asserts that two lists are equal in value and order.
     * <br><br>
     * For example, (a, b) is equal to (a, b)
     * Does not expect null as an input value.
     * Two empty lists are considered equal.
     * <br><br>
     * @param failureMessage a failureMessage that should be shown if this assertion fails
     */
    public static <T> void assertEquals(List<T> left, List<T> right, String failureMessage) {
        if (left.size() != right.size()) {
            throw new TestFailureException(
                    String.format("different sizes: left was %d, right was %d. %s", left.size(), right.size(), failureMessage));
        }
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).equals(right.get(i))) {
                throw new TestFailureException(
                        String.format("different values - left: \"%s\" right: \"%s\". %s", showWhiteSpace(left.get(i).toString()), showWhiteSpace(right.get(i).toString()), failureMessage));
            }
        }
    }

    public static void assertTrue(boolean value) {
      assertTrue(value, "");
    }

    /**
     * Assert that something is true, and show a message if it fails. This
     * is also handy for including a kind of documentation in your test
     * code.  So, please carefully note this example of its use, because
     * there's a certain subtlety at play:
     * <p>
     *     <pre>
     *      {@code assertTrue(foo == true, "foo must be true");}
     *     </pre>
     * </p>
     * <p>
     * Notice something here: The message is a statement about what *should*
     * be true.  Sometimes, I see people who do it wrong here - they
     * add a message like *foo was wrong*, but that's a disconcerting
     * thing to see in a test.  Do it like the example above, instead.
     * </p>
     * <p>
     *     One other detail to mention: If this test fails, it doesn't really
     *     give you much help about what the value should have been, it merely
     *     insists it be true.  In some cases, like where you are
     *     asserting that a string contains a substring, it is handy to include
     *     what you were looking for and what the string was as part of the
     *     failure message.
     * </p>
     */
    public static void assertTrue(boolean value, String failureMessage) {
        if (!value) {
            throw new TestFailureException(failureMessage);
        }
    }

    public static void assertFalse(boolean value) {
        if (value) {
            throw new TestFailureException("value was unexpectedly true");
        }
    }

    public static void assertFalse(boolean value, String failureMessage) {
        if (value) {
            throw new TestFailureException(failureMessage);
        }
    }

    /**
     * Given a string that may have whitespace chars,
     * render it in a way we can see.
     * <p>
     *     More specifically, it will replace tabs with (TAB),
     *     newlines with (NEWLINE), carriage returns with (RETURN).
     *     Also, if the entire text is empty (it's got a 0 length), you'll
     *     get back (EMPTY), and if blank (it's full of whitespace),
     *     you'll get back (BLANK).
     * </p>
     * <p>
     *     Note that this method is not very performant.  It carries out
     *     its work through multiple string replacements, so it's
     *     basically O(3*n) (that is, it scans through
     *     the whole string three times).
     * </p>
     */
    static String showWhiteSpace(String msg) {
        if (msg == null) return "(NULL)";
        if (msg.isEmpty()) return "(EMPTY)";

        // if we have tabs, returns, newlines in the text, show them
        String text = msg
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");

        if (text.isBlank()) return "(BLANK)";
        return text;
    }

    public static Context buildTestingContext(String loggerName) {
        return buildTestingContext(loggerName, null);
    }

    /**
     * This builds a context very similarly to {@link FullSystem#buildContext()},
     * except that it uses {@link TestLogger} instead of {@link com.renomad.minum.logging.Logger}
     * @param loggerName this will assign a human-readable name to the logger's
     *                   LoggingActionQueue so we can distinguish it
     *                   when reviewing the threads
     * @param properties If you want, you can inject a properties object here, to have
     *                   greater control over your test.  Using a parameter of null here
     *                   will cause the system to obtain properties from the minum.config file
     */
    public static Context buildTestingContext(String loggerName, Properties properties) {
        var constants = new Constants(properties);
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var logger = new TestLogger(constants, executorService, loggerName);

        var context = new Context(executorService, constants);

        context.setLogger(logger);

        return context;
    }

    public static void shutdownTestingContext(Context context) {
            new ActionQueueKiller(context).killAllQueues();
            context.getLogger().stop();
            context.getExecutorService().shutdownNow();
    }

}

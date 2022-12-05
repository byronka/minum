package atqa.framework;

import java.util.List;

public class TestFramework {

    public static <T extends Exception> T assertThrows(Class<T> myEx, Runnable r) {
        return assertThrows(myEx, null, r);
    }

    // quick note about the warning suppression - we already checked that the
    // case will be valid, when we checked if (!myEx.isInstance(ex)).
    @SuppressWarnings("unchecked")
    public static <T extends Exception> T assertThrows(Class<T> myEx, String expectedMsg, Runnable r) {
        try {
            r.run();
            throw new RuntimeException("Failed to throw exception");
        } catch (Exception ex) {
            if (!myEx.isInstance(ex)) {
                String msg = String.format("This did not throw the expected exception type (%s).  Instead, (%s) was thrown", myEx, ex);
                throw new RuntimeException(msg);
            }
            if (expectedMsg != null && !ex.getMessage().equals(expectedMsg)) {
                String msg = String.format("Did not get expected message (%s). Instead, got: %s", expectedMsg, ex.getMessage());
                throw new RuntimeException(msg);
            }
            return (T) ex;
        }

    }

    /**
     * A helper for testing - assert two integers are equal
     */
    public static <T> void assertEquals(T left, T right) {
        if (! left.equals(right)) {
            throw new RuntimeException("Not equal! %nleft:  %s %nright: %s".formatted(left, right));
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
            throw new RuntimeException(String.format("different sizes: left was %d, right was %d%n", left.size(), right.size()));
        }
        List<? extends CharSequence> orderedLeft = left.stream().sorted().toList();
        List<? extends CharSequence> orderedRight = right.stream().sorted().toList();

        for (int i = 0; i < left.size(); i++) {
            if (!orderedLeft.get(i).equals(orderedRight.get(i))) {
                throw new RuntimeException(
                        String.format(
                                "%n%ndifferent values:%n%nleft:  %s%nright: %s%n%nfull left:%n-----------%n%s%n%nfull right:%n-----------%n%s%n",
                                orderedLeft.get(i),
                                orderedRight.get(i),
                                String.join("\n", left),
                                String.join("\n", right)));
            }
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
            throw new RuntimeException(
                    String.format("different sizes: left was %d, right was %d. %s", left.size(), right.size(), failureMessage));
        }
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).equals(right.get(i))) {
                throw new RuntimeException(
                        String.format("different values - left: \"%s\" right: \"%s\". %s", left.get(i), right.get(i), failureMessage));
            }
        }
    }

    public static void assertTrue(boolean value) {
        if (!value) {
            throw new RuntimeException("value was unexpectedly false");
        }
    }

    public static void assertFalse(boolean value) {
        if (value) {
            throw new RuntimeException("value was unexpectedly true");
        }
    }

}

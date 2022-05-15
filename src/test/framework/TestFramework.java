package framework;

import java.util.List;

public class TestFramework {

    public static <T extends Exception> void assertThrows(Class<T> myEx, Runnable r) {
        assertThrows(myEx, null, r);
    }

    public static <T extends Exception> void assertThrows(Class<T> myEx, String expectedMsg, Runnable r) {
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
     */
    public static <T> void assertEqualsDisregardOrder(List<T> left, List<T> right) {
        if (left.size() != right.size()) {
            throw new RuntimeException(String.format("different sizes: left was %d, right was %d%n", left.size(), right.size()));
        }
        List<T> orderedLeft = left.stream().sorted().toList();
        List<T> orderedRight = right.stream().sorted().toList();

        for (int i = 0; i < left.size(); i++) {
            if (!orderedLeft.get(i).equals(orderedRight.get(i))) {
                throw new RuntimeException(String.format("different values: left: %s right: %s", orderedLeft.get(i), orderedRight.get(i)));
            }
        }
    }

    /**
     * asserts that two lists are equal in value and order.
     * For example, (a, b) is equal to (a, b)
     * Does not expect null as an input value.
     * Two empty lists are considered equal.
     */
    public static <T> void assertEquals(List<T> left, List<T> right) {
        if (left.size() != right.size()) {
            throw new RuntimeException(String.format("different sizes: left was %d, right was %d%n", left.size(), right.size()));
        }
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).equals(right.get(i))) {
                throw new RuntimeException(String.format("different values: left: %s right: %s", left.get(i), right.get(i)));
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

    /**
     * Commonly used in try-catch to make sure we don't get past the
     * point where an exception should have been thrown
     * @param s
     */
    public static void failTest(String s) {
        System.out.println(s);
        System.exit(1);
    }

}

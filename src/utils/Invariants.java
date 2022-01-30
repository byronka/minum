package utils;

/**
 * Utilities for asserting invariants within the code
 */
public class Invariants {

    /**
     * Specify something which must be true
     * @param predicate the boolean expression that must be true at this point
     * @param message a message that will be included in the exception if this is false
     */
    public static void require(boolean predicate, String message) {
        if (!predicate) {
            throw new RuntimeException(message);
        }
    }

    public static void requireNotNull(Object object) {
        if (object == null) {
            throw new RuntimeException(object.toString() + " must not be null");
        }
    }
}

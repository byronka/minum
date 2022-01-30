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
    public static void mustBeTrue(boolean predicate, String message) {
        if (!predicate) {
            throw new InvariantException(message);
        }
    }

    /**
     * Specify something which must be false
     * @param predicate the boolean expression that must be false at this point
     * @param message a message that will be included in the exception if this is true
     */
    public static void mustBeFalse(boolean predicate, String message) {
        if (predicate) {
            throw new InvariantException(message);
        }
    }

    public static void mustNotBeNull(Object object) {
        if (object == null) {
            throw new InvariantException("value must not be null");
        }
    }
}

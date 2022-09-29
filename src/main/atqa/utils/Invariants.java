package atqa.utils;

/**
 * Utilities for asserting invariants within the code
 */
public class Invariants {

    /**
     * Specify something which must be true.
     * <p>
     * Throws an {@link InvariantException} if false
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
     * <p>
     * Throws an {@link InvariantException} if true
     * @param predicate the boolean expression that must be false at this point
     * @param message a message that will be included in the exception if this is true
     */
    public static void mustBeFalse(boolean predicate, String message) {
        if (predicate) {
            throw new InvariantException(message);
        }
    }

    /**
     * specifies that the paramter must be not null.
     * <p>
     * Throws an {@link InvariantException} if null.
     * @return the object if not null
     */
    public static <T> T mustNotBeNull(T object) {
        if (object == null) {
            throw new InvariantException("value must not be null");
        } else {
            return object;
        }
    }
}

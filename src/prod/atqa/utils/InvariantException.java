package atqa.utils;

/**
 * An exception specific to our invariants.  See {@link Invariants}
 */
public class InvariantException extends RuntimeException {
    public InvariantException(String msg) {
        super(msg);
    }
}

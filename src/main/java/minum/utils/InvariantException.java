package minum.utils;

/**
 * An exception specific to our invariants.  See {@link Invariants}
 */
@SuppressWarnings("serial")
public class InvariantException extends RuntimeException {
    public InvariantException(String msg) {
        super(msg);
    }
}

package atqa.exceptions;

import java.io.Serial;

/**
 * This is thrown when the user action is prevented by a
 * restriction we put on the system.
 * <br>
 * For example, no user is allowed to send more than
 * Constants.MAX_READ_LINE_SIZE_BYTES to an endpoint. If
 * they do, we'll stop reading and throw this exception.
 * <br>
 * See Constants.java
 */
public class ForbiddenUseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -1588862919515625579L;

    public ForbiddenUseException(String msg) {
        super(msg);
    }

}

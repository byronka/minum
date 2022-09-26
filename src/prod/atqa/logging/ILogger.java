package atqa.logging;

import atqa.utils.ThrowingSupplier;

public interface ILogger {

    /**
     * For atqa.logging data that is helpful to debugging a running
     * system.
     * @param msg example: () -> "Hello"
     */
    void logDebug(ThrowingSupplier<String, Exception> msg);

    void logImperative(String msg);

    /**
     * Stop the {@link atqa.utils.ActionQueue} running inside the logger
     */
    void stop();
}

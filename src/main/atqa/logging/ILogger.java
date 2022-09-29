package atqa.logging;

import atqa.utils.ThrowingSupplier;

public interface ILogger {

    /**
     * For atqa.logging data that is helpful to debugging a running
     * system.
     * @param msg example: () -> "Hello"
     */
    void logDebug(ThrowingSupplier<String, Exception> msg);

    /**
     * Use this particularly for those cases where we want to log an error
     * but we're stuck inside an asynchronous piece of code, for example,
     * anything unexpectedly bad that happens in {@link atqa.utils.ActionQueue}
     * @param msg example: () -> "Hello"
     */
    void logAsyncError(ThrowingSupplier<String, Exception> msg);

    void logImperative(String msg);

    /**
     * Stop the {@link atqa.utils.ActionQueue} running inside the logger
     */
    void stop();
}

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
     * this is similar to {@link #logDebug(ThrowingSupplier)} except that it
     * gets run *A LOT*, which can spam up the logs.  Not to mention, that can
     * have performance impacts.  By default, we won't show trace information
     * unless requested.
     * @param msg example: () -> "Hello"
     */
    void logTrace(ThrowingSupplier<String, Exception> msg);

    /**
     * Use this particularly for those cases where we want to log an error
     * but we're stuck inside an asynchronous piece of code, for example,
     * anything unexpectedly bad that happens in {@link atqa.utils.ActionQueue}
     * @param msg example: () -> "Hello"
     */
    void logAsyncError(ThrowingSupplier<String, Exception> msg);

    /**
     * This is for logging business-related topics
     */
    void logAudit(ThrowingSupplier<String, Exception> msg);
}

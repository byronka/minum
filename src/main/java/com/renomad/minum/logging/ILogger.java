package com.renomad.minum.logging;

/**
 * The functions necessary for logging runtime information
 */
public interface ILogger {

    /**
     * For minum.logging data that is helpful to debugging a running
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
     * anything unexpectedly bad that happens in {@link minum.utils.ActionQueue}
     * @param msg example: () -> "Hello"
     */
    void logAsyncError(ThrowingSupplier<String, Exception> msg);

    /**
     * This is for logging business-related topics
     */
    void logAudit(ThrowingSupplier<String, Exception> msg);

    /**
     * When we are shutting down the system it is necessary to
     * explicitly stop the logger.
     *
     * <p>
     * The logger has to stand apart from the rest of the system,
     * or else we'll have circular dependencies.
     * </p>
     */
    void stop();
}

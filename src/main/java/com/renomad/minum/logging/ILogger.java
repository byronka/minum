package com.renomad.minum.logging;

import java.util.Map;

/**
 * Logging code interface
 */
public interface ILogger {

    /**
     * Logs helpful debugging information
     * @param msg a lambda for what is to be logged.  example: () -> "Hello"
     */
    void logDebug(ThrowingSupplier<String, Exception> msg);

    /**
     * Logs helpful debugging information
     * <p>
     *     Similar to {@link #logDebug(ThrowingSupplier)} but used
     *     for code that runs very often, requires extra calculation, or has
     *     data of large size.
     * </p>
     * <p>
     *     It is possible to disable trace logs and thus avoid performance impacts unless
     *     the data is needed for deeper investigation.
     * </p>
     * @param msg a lambda for what is to be logged.  example: () -> "Hello"
     */
    void logTrace(ThrowingSupplier<String, Exception> msg);

    /**
     * Logs helpful debugging information inside threads
     * @param msg a lambda for what is to be logged.  example: () -> "Hello"
     */
    void logAsyncError(ThrowingSupplier<String, Exception> msg);

    /**
     * This is for logging business-related topics
     * <p>
     *     This log type is expected to be printed least-often, and should
     *     directly relate to a user action.  An example would
     *     be "New user created: alice"
     * </p>
     * msg a lambda for what is to be logged.  example: () -> "Hello"
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

    /**
     * This method can be used to adjust the active log levels, which
     * is a mapping of keys of {@link LoggingLevel} to boolean values.
     * If the boolean value is true, that level of logging is enabled.
     */
    Map<LoggingLevel, Boolean> getActiveLogLevels();
}

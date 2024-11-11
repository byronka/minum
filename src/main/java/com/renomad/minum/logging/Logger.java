package com.renomad.minum.logging;

import com.renomad.minum.state.Constants;
import com.renomad.minum.queue.AbstractActionQueue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static com.renomad.minum.utils.TimeUtils.getTimestampIsoInstant;

/**
 * Implementation of {@link ILogger}
 */
public class Logger implements ILogger {
    /**
     * The {@link LoggingActionQueue} that handles all
     * our messages thread-safely by taking
     * them off the top of a queue.
     */
    protected final AbstractActionQueue loggingActionQueue;
    private final Constants constants;
    private final ExecutorService executorService;
    private final String name;
    private final Map<LoggingLevel, Boolean> activeLogLevels;

    /**
     * Constructor
     * @param constants used for determining enabled log levels
     * @param executorService provides thread handling for the logs, used to
     *                        build a {@link LoggingActionQueue}
     * @param name sets a name on the {@link LoggingActionQueue} to aid debugging, to
     *             help distinguish queues.
     */
    public Logger(Constants constants, ExecutorService executorService, String name) {
        this(constants, executorService, name, null);
    }

    private Logger(Constants constants, ExecutorService executorService, String name, AbstractActionQueue loggingActionQueue) {
        this.constants = constants;
        this.executorService = executorService;
        this.name = name;
        // this tricky code exists so that a user has the option to create a class extended
        // from this one, and can construct it with the logger instance, making it possible
        // to inject the running action queue.  This enables us to continue using the same
        // action queue amongst descendant classes.
        if (loggingActionQueue == null) {
            this.loggingActionQueue = new LoggingActionQueue("loggerPrinter" + name, executorService, constants).initialize();
        } else {
            this.loggingActionQueue = loggingActionQueue;
        }
        activeLogLevels = convertToMap(constants.logLevels);
    }

    /**
     * A constructor meant for use by descendant classes
     * @param logger an existing instance of a running logger, needed in order to have the
     *               descendant logger using the same {@link AbstractActionQueue}, which is
     *               necessary so logs don't interleave with each other.
     */
    public Logger(Logger logger) {
        this(logger.constants, logger.executorService, logger.name, logger.loggingActionQueue);
    }

    /**
     * Convert the list of enabled log levels to a map of enum -> boolean
     */
    static Map<LoggingLevel, Boolean> convertToMap(List<LoggingLevel> enabledLoggingLevels) {
        Map<LoggingLevel, Boolean> activeLogLevels = new EnumMap<>(LoggingLevel.class);
        for (LoggingLevel t : LoggingLevel.values()) {
            activeLogLevels.put(t, enabledLoggingLevels.contains(t));
        }
        return activeLogLevels;
    }

    @Override
    public void logDebug(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, LoggingLevel.DEBUG, activeLogLevels, loggingActionQueue);
    }

    @Override
    public void logTrace(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, LoggingLevel.TRACE, activeLogLevels, loggingActionQueue);
    }

    @Override
    public void logAudit(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, LoggingLevel.AUDIT, activeLogLevels, loggingActionQueue);
    }

    @Override
    public void stop() {
        this.loggingActionQueue.stop();
        this.executorService.shutdownNow();
    }

    @Override
    public void logAsyncError(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, LoggingLevel.ASYNC_ERROR, activeLogLevels, loggingActionQueue);
    }

    @Override
    public Map<LoggingLevel, Boolean> getActiveLogLevels() {
        return activeLogLevels;
    }

    /**
     * A helper method to reduce duplication
     */
    static void logHelper(
            ThrowingSupplier<String, Exception> msg,
            LoggingLevel loggingLevel,
            Map<LoggingLevel, Boolean> activeLogLevels,
            AbstractActionQueue loggingActionQueue
            ) {
        if (Boolean.TRUE.equals(activeLogLevels.get(loggingLevel))) {
        String receivedMessage;
            try {
                receivedMessage = msg.get();
            } catch (Exception ex) {
                receivedMessage = "EXCEPTION DURING GET: " + ex;
            }
            String finalReceivedMessage = receivedMessage;
            if (loggingActionQueue == null || loggingActionQueue.isStopped()) {
                Object[] args = new Object[]{getTimestampIsoInstant(), loggingLevel.name(), showWhiteSpace(finalReceivedMessage)};
                System.out.printf("%s\t%s\t%s%n", args);
            } else {
                loggingActionQueue.enqueue("Logger#logHelper(" + receivedMessage + ")", () -> {
                    Object[] args = new Object[]{getTimestampIsoInstant(), loggingLevel.name(), showWhiteSpace(finalReceivedMessage)};
                    System.out.printf("%s\t%s\t%s%n", args);
                });
            }
        }
    }

    /**
     * Given a string that may have whitespace chars, render it in a way we can see
     */
    static String showWhiteSpace(String msg) {
        if (msg == null) return "(NULL)";
        if (msg.isEmpty()) return "(EMPTY)";

        // if we have tabs, returns, newlines in the text, show them
        String text = msg
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");

        if (text.isBlank()) return "(BLANK)";
        return text;
    }

}

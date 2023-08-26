package minum.logging;

import minum.Constants;
import minum.Context;
import minum.utils.ExtendedExecutor;
import minum.utils.FileUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static minum.utils.TimeUtils.getTimestampIsoInstant;

/**
 * Implementation of {@link ILogger}
 */
public class Logger implements ILogger {
    /**
     * The {@link LoggingActionQueue} that handles all
     * our messages thread-safely by taking
     * them off the top of a queue.
     */
    protected final LoggingActionQueue loggingActionQueue;
    private final ExecutorService executorService;
    private Map<LoggingLevel, Boolean> activeLogLevels;

    public Logger(Constants constants, ExecutorService executorService, String name) {
        this.executorService = executorService;
        loggingActionQueue = new LoggingActionQueue("loggerPrinter" + name, executorService, constants).initialize();
        toggleDefaultLogging(constants.LOG_LEVELS);
    }

    /**
     * for the various kinds of minum.logging which can be enabled/disabled,
     * turn on the expected types of minum.logging.
     */
    private void toggleDefaultLogging(List<LoggingLevel> enabledLoggingLevels) {
        activeLogLevels = new EnumMap<>(LoggingLevel.class);
        for (LoggingLevel t : LoggingLevel.values()) {
            activeLogLevels.put(t, enabledLoggingLevels.contains(t));
        }
    }

    @Override
    public void logDebug(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, LoggingLevel.DEBUG);
    }

    @Override
    public void logTrace(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, LoggingLevel.TRACE);
    }

    @Override
    public void logAudit(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, LoggingLevel.AUDIT);
    }

    @Override
    public void stop() {
        this.loggingActionQueue.stop();
        this.executorService.shutdownNow();
    }

    @Override
    public void logAsyncError(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, LoggingLevel.ASYNC_ERROR);
    }

    /**
     * A helper method to reduce duplication
     */
    private void logHelper(ThrowingSupplier<String, Exception> msg, LoggingLevel loggingLevel) {
        if (activeLogLevels.get(loggingLevel)) {
        String receivedMessage;
            try {
                receivedMessage = msg.get();
            } catch (Exception ex) {
                receivedMessage = "EXCEPTION DURING GET: " + ex;
            }
            String finalReceivedMessage = receivedMessage;
            loggingActionQueue.enqueue("Logger#logHelper("+receivedMessage+")", () -> {
                Object[] args = new Object[]{getTimestampIsoInstant(), loggingLevel.name(), showWhiteSpace(finalReceivedMessage)};
                System.out.printf("%s\t%s\t%s%n", args);
            });
        }
    }

    /**
     * Given a string that may have whitespace chars, render it in a way we can see
     */
    private static String showWhiteSpace(String msg) {
        if (msg == null) return "(NULL)";
        // if we have tabs, returns, newlines in the text, show them
        String text = msg
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        // if the text is an empty string, render that
        text = text.isEmpty() ? "(EMPTY)" : text;
        // if the text is nothing but whitespace, show that
        text = text.isBlank() ? "(BLANK)" : text;
        return text;
    }

}

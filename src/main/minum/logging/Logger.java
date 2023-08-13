package minum.logging;

import minum.Constants;
import minum.Context;
import minum.utils.ExtendedExecutor;

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
    private final Context context;
    private Map<LoggingLevel, Boolean> activeLogLevels;

    /**
     * This constructor initializes an {@link LoggingActionQueue}
     * to handle log messages.
     */
    public Logger(Context context) {
        this(context, "");
    }

    public Logger(Context context, String name) {
        this.context = context;
        this.context.setLogger(this);
        Constants constants = context.getConstants();
        loggingActionQueue = new LoggingActionQueue("loggerPrinter" + name, context.getExecutorService(), context.getConstants()).initialize();
        toggleDefaultLogging(constants.LOG_LEVELS);
    }

    /**
     * Build a logger.
     *
     * <p>
     *     Some interesting aspects of this builder:
     * </p>
     * <ul>
     *     <li>
     *         It uses its own {@link ExecutorService}, separate from
     *         the one for the rest of the system.
     *     </li>
     *     <li>
     *         It uses its own Context object, separate from the regular {@link Context}
     *     </li>
     * </ul>
     */
    public static ILogger make(Context context) {
        return new Logger(context, "_primary_system_logger");
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
        this.context.getExecutorService().shutdownNow();
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

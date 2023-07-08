package minum.logging;

import minum.Constants;
import minum.Context;
import minum.utils.ActionQueue;
import minum.utils.ThrowingSupplier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static minum.utils.TimeUtils.getTimestampIsoInstant;

/**
 * Implementation of {@link ILogger}
 */
public class Logger implements ILogger {
    /**
     * The {@link ActionQueue} that handles all
     * our messages thread-safely by taking
     * them off the top of a queue.
     */
    protected final ActionQueue loggerPrinter;
    private Map<LoggingLevel, Boolean> activeLogLevels;

    /**
     * This constructor initializes an {@link ActionQueue}
     * to handle log messages.
     */
    public Logger(Context context) {
        Constants constants = context.getConstants();
        loggerPrinter = new ActionQueue("loggerPrinter", context).initialize();
        toggleDefaultLogging(constants.LOG_LEVELS);
    }

    /**
     * Given a list of strings representing logging levels,
     * convert it to a list of enums.  Log levels are enumerated
     * in {@link LoggingLevel}.
     */
    public static List<LoggingLevel> convertLoggingStringsToEnums(List<String> logLevels) {
        List<String> logLevelStrings = logLevels.stream().map(String::toUpperCase).toList();
        List<LoggingLevel> enabledLoggingLevels = new ArrayList<>();
        for (LoggingLevel t : LoggingLevel.values()) {
            if (logLevelStrings.contains(t.name())) {
                enabledLoggingLevels.add(t);
            }
        }
        return enabledLoggingLevels;
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
    public void logAsyncError(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, LoggingLevel.ASYNC_ERROR);
    }

    /**
     * A helper method to reduce duplication
     */
    public void logHelper(ThrowingSupplier<String, Exception> msg, LoggingLevel loggingLevel) {
        if (activeLogLevels.get(loggingLevel)) {
        String receivedMessage;
            try {
                receivedMessage = msg.get();
            } catch (Exception ex) {
                receivedMessage = "EXCEPTION DURING GET: " + ex;
            }
            String finalReceivedMessage = receivedMessage;
            loggerPrinter.enqueue("Logger#logHelper("+receivedMessage+")", () -> {
                printf(loggingLevel.name() + ": %s %s%n", getTimestampIsoInstant(), showWhiteSpace(finalReceivedMessage));
                return null;
            });
        }
    }

    /**
     * Given a string that may have whitespace chars, render it in a way we can see
     */
    public static String showWhiteSpace(String msg) {
        // if we have tabs, returns, newlines in the text, show them
        String text = msg
                .replace("\t", "(TAB)")
                .replace("\r", "(RETURN)")
                .replace("\n", "(NEWLINE)");
        // if the text is an empty string, render that
        text = text.isEmpty() ? "(EMPTY)" : text;
        // if the text is nothing but whitespace, show that
        text = text.isBlank() ? "(BLANK)" : text;
        return text;
    }

    public static void printf(String msg, Object ...args) {
        System.out.printf(msg, args);
    }

}

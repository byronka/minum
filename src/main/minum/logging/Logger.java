package minum.logging;

import minum.Constants;
import minum.utils.ActionQueue;
import minum.utils.ThrowingSupplier;

import java.util.ArrayList;
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
     * The {@link ActionQueue} that handles all
     * our messages thread-safely by taking
     * them off the top of a queue.
     */
    protected final ActionQueue loggerPrinter;
    private Map<Type, Boolean> toggles;

    /**
     * This constructor initializes an {@link ActionQueue}
     * to handle log messages.
     */
    public Logger(ExecutorService es) {
        loggerPrinter = new ActionQueue("loggerPrinter", es).initialize();
        List<String> logLevelStrings = Constants.LOG_LEVELS.stream().map(String::toUpperCase).toList();
        List<Type> enabledLoggingLevels = new ArrayList<>();
        for (Type t : Type.values()) {
            if (logLevelStrings.contains(t.name())) {
                enabledLoggingLevels.add(t);
            }
        }
        toggleDefaultLogging(enabledLoggingLevels);
    }

    /**
     * for the various kinds of minum.logging which can be enabled/disabled,
     * turn on the expected types of minum.logging.
     */
    private void toggleDefaultLogging(List<Type> enabledLoggingLevels) {
        toggles = new EnumMap<>(Type.class);
        for (Type t : Type.values()) {
            toggles.put(t, enabledLoggingLevels.contains(t));
        }
    }

    @Override
    public void logDebug(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, Type.DEBUG);
    }

    @Override
    public void logTrace(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, Type.TRACE);
    }

    @Override
    public void logAudit(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, Type.AUDIT);
    }

    @Override
    public void logAsyncError(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, Type.ASYNC_ERROR);
    }

    /**
     * A helper method to reduce duplication
     */
    public void logHelper(ThrowingSupplier<String, Exception> msg, Type type) {
        String receivedMessage;
        try {
            receivedMessage = msg.get();
        } catch (Exception ex) {
            receivedMessage = "EXCEPTION DURING GET: " + ex;
        }
        if (toggles.get(type)) {
            String finalReceivedMessage = receivedMessage;
            loggerPrinter.enqueue("Logger#logHelper("+receivedMessage+")", () -> {
                printf(type.name() + ": %s %s%n", getTimestampIsoInstant(), showWhiteSpace(finalReceivedMessage));
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

    enum Type {
        /**
         * Information useful for debugging.
         */
        DEBUG,

        /**
         * Represents an error that occurs in a separate thread, so
         * that we are not able to catch it bubbling up
         */
        ASYNC_ERROR,

        /**
         * Information marked as trace is pretty much entered for
         * the same reason as DEBUG - i.e. so we can see important
         * information about the running state of the program. The
         * only difference is that trace information is very voluminous.
         * That is, there's tons of it, and it could make it harder
         * to find the important information amongst a lot of noise.
         * For that reason, TRACE is usually turned off.
         */
        TRACE,

        /**
         * Information marked audit is for business-related stuff.  Like,
         * a new user being created.  A photo being looked for.  Stuff
         * closer to the user needs.
         */
        AUDIT
    }
}

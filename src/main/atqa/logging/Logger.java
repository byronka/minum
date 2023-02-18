package atqa.logging;

import atqa.utils.ThrowingSupplier;
import atqa.utils.ActionQueue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class Logger implements ILogger {
    protected final ActionQueue loggerPrinter;
    private Map<Type, Boolean> toggles;
    public static ILogger INSTANCE;

    public String getTimestamp() {
        return ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT);
    }

    public Logger(ExecutorService es) {
        loggerPrinter = new ActionQueue("loggerPrinter", es).initialize();
        toggleDefaultLogging();
        if (INSTANCE == null) {
            INSTANCE = this;
        }
    }

    /**
     * for the various kinds of atqa.logging which can be enabled/disabled,
     * turn on the expected types of atqa.logging.
     */
    private void toggleDefaultLogging() {
        toggles = new EnumMap<>(Type.class);
        toggles.put(Type.DEBUG, true);
        toggles.put(Type.TRACE, true);
        toggles.put(Type.ASYNC_ERROR, true);
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
    public void logAsyncError(ThrowingSupplier<String, Exception> msg) {
        logHelper(msg, Type.ASYNC_ERROR);
    }

    @Override
    public void logAsyncError(Exception ex) {
        final var msg = convertExceptionToString(ex);
        logHelper(() -> msg, Type.ASYNC_ERROR);
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
            loggerPrinter.enqueue(() -> {
                printf(type.name() + ": %s %s%n", getTimestamp(), showWhiteSpace(finalReceivedMessage));
                return null;
            });
        }
    }

    @Override
    public void logImperative(String msg) {
        System.out.printf("%s IMPERATIVE: %s%n", getTimestamp(), msg);
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
        TRACE
    }
}

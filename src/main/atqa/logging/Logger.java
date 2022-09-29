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
    }

    @Override
    public void stop() {
        loggerPrinter.stop();
    }

    @Override
    public void logDebug(ThrowingSupplier<String, Exception> msg) {
        String receivedMessage;
        try {
            receivedMessage = msg.get();
        } catch (Exception ex) {
            receivedMessage = "EXCEPTION DURING GET: " + ex;
        }
        if (toggles.get(Type.DEBUG)) {
            String finalReceivedMessage = receivedMessage;
            loggerPrinter.enqueue(() -> {
                printf("DEBUG: %s %s%n", getTimestamp(), showWhiteSpace(finalReceivedMessage));
                return null;
            });
        }
    }

    @Override
    public void logAsyncError(ThrowingSupplier<String, Exception> msg) {
        String receivedMessage;
        try {
            receivedMessage = msg.get();
        } catch (Exception ex) {
            receivedMessage = "EXCEPTION DURING GET: " + ex;
        }
        if (toggles.get(Type.ASYNC_ERROR)) {
            String finalReceivedMessage = receivedMessage;
            loggerPrinter.enqueue(() -> {
                printf("ASYNC ERROR: %s %s%n", getTimestamp(), showWhiteSpace(finalReceivedMessage));
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
        ASYNC_ERROR
    }
}

package logging;

import primary.web.ThrowingSupplier;
import utils.ActionQueue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
     * for the various kinds of logging which can be enabled/disabled,
     * turn on the expected types of logging.
     */
    private void toggleDefaultLogging() {
        toggles = new EnumMap<>(Type.class);
        toggles.put(Type.DEBUG, true);
    }

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
        DEBUG
    }

    /**
     * A helper method to skip all non-imperative logging
     */
    public Logger turnOff(Type ...type) {
        for (Type t : type) {
            if (t.equals(Type.DEBUG)) {
                toggles.put(Type.DEBUG, false);
            }
        }
        return this;
    }

    /**
     * Loop through all the keys in the toggles and set them all false
     */
    public Logger turnOffAll() {
        toggles = toggles.keySet().stream().collect(Collectors.toMap(x -> x, x -> false));
        return this;
    }
}

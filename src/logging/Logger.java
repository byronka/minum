package logging;

import utils.ActionQueue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Logger implements ILogger {
    private ExecutorService es;
    private ActionQueue loggerPrinter;
    private Map<Type, Boolean> toggles;

    public String getTimestamp() {
        return ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT);
    }

    public Logger(ExecutorService es) {
        this.es = es;
    }

    public Logger initialize() {
        loggerPrinter = new ActionQueue("loggerPrinter", es).initialize();
        toggleDefaultLogging();
        return this;
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
    public void logDebug(Supplier<String> msg) {
        if (toggles.get(Type.DEBUG)) {
            loggerPrinter.enqueue(() -> printf("DEBUG: %s %s%n", getTimestamp(), msg.get()));
        }
    }

    public static void println(String msg) {
        System.out.println(msg);
    }

    /**
     * A little helper function to log a test title prefixed with "TEST:"
     */
    public void test(String msg) {
        loggerPrinter.enqueue(() -> printf("TEST: %s%n", msg));
    }

    public static void printf(String msg, Object ...args) {
        System.out.printf(msg, args);
    }

    public static String printStackTrace(Throwable ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
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

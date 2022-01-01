package primary;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class Logger implements ILogger {
    private ExecutorService es;
    private ActionQueue loggerPrinter;

    public String getTimestamp() {
        return ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT);
    }

    public Logger(ExecutorService es) {
        this.es = es;
    }

    public Logger initialize() {
        loggerPrinter = new ActionQueue("loggerPrinter", es).initialize();
        return this;
    }

    public void stop() {
        loggerPrinter.stop();
    }

    @Override
    public void logDebug(Supplier<String> msg) {
        loggerPrinter.enqueue(() -> printf("DEBUG: %s %s%n", getTimestamp(), msg.get()));
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
}

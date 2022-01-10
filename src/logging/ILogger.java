package logging;

import java.util.function.Supplier;

public interface ILogger {

    /**
     * For logging data that is helpful to debugging a running
     * system.
     * @param msg example: () -> "Hello"
     */
    void logDebug(Supplier<String> msg);
}

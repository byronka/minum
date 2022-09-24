package logging;

import primary.web.ThrowingSupplier;

public interface ILogger {

    /**
     * For logging data that is helpful to debugging a running
     * system.
     * @param msg example: () -> "Hello"
     */
    void logDebug(ThrowingSupplier<String, Exception> msg);

    void logImperative(String msg);
}

package minum.utils;

import minum.logging.ILogger;

@FunctionalInterface
public interface ThrowingRunnable<E extends Exception>{

    void run() throws E;

    static Runnable throwingRunnableWrapper(ThrowingRunnable<Exception> throwingRunnable, ILogger logger) {
        return () -> {
            try {
                throwingRunnable.run();
            } catch (Exception ex) {
                logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
                throw new RuntimeException(ex);
            }
        };
    }
}
package minum.utils;

import minum.logging.ILogger;

import java.util.function.Supplier;

@FunctionalInterface
public interface ThrowingSupplier<T, E extends Exception>{

    T get() throws E;

    static <T> Supplier<T> throwingSupplierWrapper(ThrowingSupplier<T, Exception> throwingSupplier, ILogger logger) {
        return () -> {
            try {
                return throwingSupplier.get();
            } catch (Exception ex) {
                logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
                throw new RuntimeException(ex);
            }
        };
    }
}
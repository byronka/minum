package primary.web;

import java.util.function.Supplier;

@FunctionalInterface
public interface ThrowingSupplier<T, E extends Exception>{

    T get() throws E;

    static <T> Supplier<T> throwingSupplierWrapper(ThrowingSupplier<T, Exception> throwingSupplier) {
        return () -> {
            try {
                return throwingSupplier.get();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
    }
}
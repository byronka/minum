package com.renomad.minum.logging;

/**
 * a functional interface used in {@link ILogger}, allows exceptions
 * to bubble up.
 */
@FunctionalInterface
public interface ThrowingSupplier<T, E extends Throwable>{

    T get() throws E;

}
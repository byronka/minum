package com.renomad.minum.utils;

import java.util.concurrent.Callable;

/**
 * This class is to improve maintainability in the system.  It makes
 * possible reviewing the queue of actions and more easily understanding
 * the purpose of each Callable.
 */
public final class RunnableWithDescription<E extends Throwable> implements ThrowingRunnable<E> {

    private final String description;
    private final ThrowingRunnable<E> r;

    /**
     * By constructing a {@link ThrowingRunnable} here, you can
     * provide a description of the runnable that will be reviewable
     * during debugging.
     */
    public RunnableWithDescription(ThrowingRunnable<E> r, String description) {
        this.description = description;
        this.r = r;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return getDescription();
    }

    @Override
    public void run() throws E {
        r.run();
    }
}

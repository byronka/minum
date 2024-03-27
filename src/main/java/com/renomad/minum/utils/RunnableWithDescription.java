package com.renomad.minum.utils;

/**
 * This class is to improve maintainability in the system.  It makes
 * possible reviewing the queue of actions and more easily understanding
 * the purpose of each Callable.
 */
public final class RunnableWithDescription implements ThrowingRunnable {

    private final String description;
    private final ThrowingRunnable r;

    /**
     * By constructing a {@link ThrowingRunnable} here, you can
     * provide a description of the runnable that will be reviewable
     * during debugging.
     */
    public RunnableWithDescription(ThrowingRunnable r, String description) {
        this.description = description;
        this.r = r;
    }

    @Override
    public String toString() {
        return description;
    }

    @Override
    public void run() {
        try {
            r.run();
        } catch (Exception e) {
            throw new UtilsException(e);
        }
    }
}

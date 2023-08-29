package com.renomad.minum.utils;

/**
 * This class exists just to avoid needing to handle
 * the exception when I use a regular Thread.sleep()
 */
public final class MyThread {

    private MyThread() {
        // cannot construct
    }

    /**
     * Same behavior as {@link Thread#sleep(long)}, but
     * wrapped so that it prints the exception's stacktrace
     * instead of letting it bubble up.
     * @param millis length of time in milliseconds.
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}

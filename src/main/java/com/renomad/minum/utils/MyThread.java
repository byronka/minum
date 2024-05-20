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
     * @return false if the sleep succeeded without being interrupted
     */
    public static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            handleInterrupted(e);
            return false;
        }
        return true;
    }

    static void handleInterrupted(InterruptedException e) {
        System.out.println("Interruption during MyThread.sleep: " + e);
        Thread.currentThread().interrupt();
    }
}

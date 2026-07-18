package com.renomad.minum.database;


import java.util.concurrent.locks.ReentrantLock;

/**
 * A child class which provides access to
 * the {@link ReentrantLock#getOwner()} method,
 * useful for logging who a thread is waiting on
 * when multiple are contending.
 */
public class InspectableLock extends ReentrantLock {

    /**
     * Provide access to the protected method, {@link ReentrantLock#getOwner()}
     */
    public Thread getLockOwner() {
        return this.getOwner();
    }

    /**
     * A helper method to get the identifier
     * for a thread, handling null when necessary
     */
    public static String getLockOwnerIdString(Thread thread) {
        if (thread == null) {
            return "null";
        } else {
            return String.valueOf(thread.threadId());
        }
    }
}

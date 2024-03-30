package com.renomad.minum.utils;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class provides the ability to pop items into
 * a queue thread-safely and know they'll happen later.
 * <p>
 * For example, this is helpful for minum.logging, or passing
 * functions to a minum.database.  It lets us run a bit faster,
 * since the I/O actions are happening on a separate
 * thread and the only time required is passing the
 * function of what we want to run later.
 */
public interface AbstractActionQueue {

    /**
     * Start the inner loop - in other words, start the queue's processing
     */
    AbstractActionQueue initialize();

    /**
     * Adds something to the queue to be processed.
     * <p>
     *     Here is an example use of .enqueue:
     * </p>
     * <p>
     * <pre>
     * {@code   actionQueue.enqueue("Write person file to disk at " + filePath, () -> {
     *             Files.writeString(filePath, pf.serialize());
     *         });}
     * </pre>
     * </p>
     */
    void enqueue(String description, ThrowingRunnable action);

    /**
     * Stops the action queue
     * @param count how many loops to wait before we crash it closed
     * @param sleepTime how long to wait in milliseconds between loops
     */
    void stop(int count, int sleepTime);

    /**
     * This will prevent any new actions being
     * queued (by setting the stop flag to true and thus
     * causing an exception to be thrown
     * when a call is made to [enqueue]) and will
     * block until the queue is empty.
     */
    void stop();

    /**
     * This returns the thread that is being used to run the
     * infinite central loop
     */
    Thread getQueueThread();

    /**
     * Get the {@link java.util.Queue} of data that is supposed to get
     * processed.
     */
    LinkedBlockingQueue<RunnableWithDescription> getQueue();

    /**
     * Indicate whether this has had its {@link #stop()} method completed.
     */
    boolean isStopped();
}

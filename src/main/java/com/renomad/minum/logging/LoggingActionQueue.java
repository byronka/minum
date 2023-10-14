package com.renomad.minum.logging;

import com.renomad.minum.Constants;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.RunnableWithDescription;
import com.renomad.minum.utils.ThrowingRunnable;
import com.renomad.minum.utils.TimeUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class is very similar to {@link com.renomad.minum.utils.ActionQueue} but is
 * focused on Logging.
 * <p>
 *     The only reason it was necessary to create a wholly separate stack for logging
 *     is that logging has so many circular dependencies with the rest of the system,
 *     this was the only way to free ourselves during initialization / shutdown.
 * </p>
 */
final class LoggingActionQueue {
    private final String name;
    private final ExecutorService executorService;
    private final LinkedBlockingQueue<RunnableWithDescription> queue;
    private boolean stop = false;
    private final Constants constants;

    LoggingActionQueue(String name, ExecutorService executorService, Constants constants) {
        this.name = name;
        this.executorService = executorService;
        this.queue = new LinkedBlockingQueue<>();
        this.constants = constants;
    }

    // Regarding the InfiniteLoopStatement - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @SuppressWarnings("InfiniteLoopStatement")
    LoggingActionQueue initialize() {
        Runnable queueThread = () -> {
            Thread.currentThread().setName(name);
            try {
                while (true) {
                    runAction();
                }
            } catch (InterruptedException ex) {
                /*
                this is what we expect to happen.
                once this happens, we just continue on.
                this only gets called when we are trying to shut everything
                down cleanly
                 */
                if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.printf("%s LoggingActionQueue for %s is stopped.%n", TimeUtils.getTimestampIsoInstant(), name);
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                System.out.printf("%s ERROR: LoggingActionQueue for %s has stopped unexpectedly. error: %s%n", TimeUtils.getTimestampIsoInstant(), name, ex);
                throw ex;
            }
        };
        executorService.submit(queueThread);
        return this;
    }

    private void runAction() throws InterruptedException {
        RunnableWithDescription action = queue.take();
        try {
            action.run();
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }

    /**
     * Adds something to the queue to be processed.
     * @param action an action to take with no return value.  (this uses callable so we can collect exceptions)
     */
    void enqueue(String description, ThrowingRunnable action) {
        if (! stop) {
            queue.add(new RunnableWithDescription(action, description));
        }
    }

    /**
     * Stops the action queue
     * @param count how many loops to wait before we crash it closed
     * @param sleepTime how long to wait in milliseconds between loops
     */
    void stop(int count, int sleepTime) {
        if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.printf("%s Stopping queue %s%n", TimeUtils.getTimestampIsoInstant(), this);
        stop = true;
        for (int i = 0; i < count; i++) {
            if (queue.isEmpty()) return;
            if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.printf("%s Queue not yet empty, has %d elements. waiting...%n", TimeUtils.getTimestampIsoInstant(), queue.size());
            MyThread.sleep(sleepTime);
        }
        System.out.printf("%s Queue %s has %d elements left but we're done waiting.  Queue toString: %s",TimeUtils.getTimestampIsoInstant(), this, queue.size(), queue);
    }

    /**
     * This will prevent any new actions being
     * queued (by setting the stop flag to true and thus
     * causing an exception to be thrown
     * when a call is made to [enqueue]) and will
     * block until the queue is empty.
     */
    public void stop() {
        stop(5, 20);
    }

    @Override
    public String toString() {
        return this.name;
    }

}
package com.renomad.minum.logging;

import com.renomad.minum.queue.AbstractActionQueue;
import com.renomad.minum.queue.ActionQueue;
import com.renomad.minum.state.Constants;
import com.renomad.minum.utils.*;

import java.util.Map;
import java.util.concurrent.*;

/**
 * This class is very similar to {@link ActionQueue} but is
 * focused on Logging.
 * <p>
 *     It is necessary to create independent classes for logging to avoid circular dependencies
 * </p>
 */
final class LoggingActionQueue implements AbstractActionQueue {
    private final String name;
    private final ExecutorService executorService;
    private final LinkedBlockingQueue<RunnableWithDescription> queue;
    private Thread queueThread;
    private boolean stop = false;
    private boolean isStoppedStatus = false;
    private final Map<LoggingLevel, Boolean> enabledLogLevels;

    LoggingActionQueue(String name, ExecutorService executorService, Constants constants) {
        this.name = name;
        this.executorService = executorService;
        this.queue = new LinkedBlockingQueue<>();
        this.enabledLogLevels = Logger.convertToMap(constants.logLevels);
    }

    // Regarding the InfiniteLoopStatement - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that is what it is.
    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public AbstractActionQueue initialize() {
        Runnable centralLoop = () -> {
            Thread.currentThread().setName(name);
            this.queueThread = Thread.currentThread();
            try {
                while (true) {
                    runAction(queue.take());
                }
            } catch (InterruptedException ex) {
                /*
                this is what we expect to happen.
                once this happens, we just continue on.
                this only gets called when we are trying to shut everything
                down cleanly
                 */
                Logger.logHelper(() -> String.format("%s LoggingActionQueue for %s is stopped.%n", TimeUtils.getTimestampIsoInstant(), name), LoggingLevel.DEBUG, enabledLogLevels, this);
                Thread.currentThread().interrupt();
            }
        };
        executorService.submit(centralLoop);
        return this;
    }

    static void runAction(RunnableWithDescription action) {
        action.run();
    }

    /**
     * Adds something to the queue to be processed.
     */
    @Override
    public void enqueue(String description, ThrowingRunnable action) {
        if (! stop) {
            queue.add(new RunnableWithDescription(action, description));
        }
    }

    /**
     * Stops the action queue
     * @param count how many loops to wait before we crash it closed
     * @param sleepTime how long to wait in milliseconds between loops
     */
    @Override
    public void stop(int count, int sleepTime) {
        Logger.logHelper(() -> String.format("%s Stopping queue %s%n", TimeUtils.getTimestampIsoInstant(), this), LoggingLevel.DEBUG, enabledLogLevels, this);
        stop = true;
        for (int i = 0; i < count; i++) {
            if (queue.isEmpty()) return;
            Logger.logHelper(() -> String.format("%s Queue not yet empty, has %d elements. waiting...%n", TimeUtils.getTimestampIsoInstant(), queue.size()), LoggingLevel.DEBUG, enabledLogLevels, this);
            MyThread.sleep(sleepTime);
        }
        isStoppedStatus = true;
        Logger.logHelper(() -> String.format("%s Queue %s has %d elements left but we're done waiting.  Queue toString: %s",TimeUtils.getTimestampIsoInstant(), this, queue.size(), queue), LoggingLevel.DEBUG, enabledLogLevels, this);
    }

    /**
     * This will prevent any new actions being
     * queued (by setting the stop flag to true and thus
     * causing an exception to be thrown
     * when a call is made to [enqueue]) and will
     * block until the queue is empty.
     */
    @Override
    public void stop() {
        stop(5, 20);
    }

    public Thread getQueueThread() {
        return queueThread;
    }

    @Override
    public LinkedBlockingQueue<RunnableWithDescription> getQueue() {
        return queue;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public boolean isStopped() {
        return isStoppedStatus;
    }
}
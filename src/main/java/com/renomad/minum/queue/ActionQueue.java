package com.renomad.minum.queue;

import com.renomad.minum.state.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.*;

import java.util.concurrent.*;

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
public final class ActionQueue implements AbstractActionQueue {
    private final String name;
    private final ExecutorService queueExecutor;
    private final LinkedBlockingQueue<RunnableWithDescription> queue;
    private final ILogger logger;
    private boolean stop = false;
    private Thread queueThread;
    private boolean isStoppedStatus;

    /**
     * See the {@link ActionQueue} description for more detail. This
     * constructor will build your new action queue and handle registering
     * it with a list of other action queues in the {@link Context} object.
     * @param name give this object a unique, explanatory name.
     */
    public ActionQueue(String name, Context context) {
        this.name = name;
        this.queueExecutor = context.getExecutorService();
        this.queue = new LinkedBlockingQueue<>();
        context.getActionQueueState().offerToQueue(this);
        this.logger = context.getLogger();
    }

    // Regarding the InfiniteLoopStatement - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    public ActionQueue initialize() {
        Runnable centralLoop = () -> {
            Thread.currentThread().setName(name);
            this.queueThread = Thread.currentThread();
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
                logger.logDebug(() -> String.format("%s ActionQueue for %s is stopped.%n", TimeUtils.getTimestampIsoInstant(), name));
                Thread.currentThread().interrupt();
            }
        };
        queueExecutor.submit(centralLoop);
        return this;
    }

    private void runAction() throws InterruptedException {
        RunnableWithDescription action = queue.take();
        try {
            action.run();
        } catch (Exception e) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(e));
        }
    }

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
    @Override
    public void enqueue(String description, ThrowingRunnable action) {
        if (! stop) {
            queue.add(new RunnableWithDescription(action, description));
        } else {
            throw new UtilsException(String.format("failed to enqueue %s - ActionQueue \"%s\" is stopped", description, this.name));
        }
    }

    /**
     * Stops the action queue
     * @param count how many loops to wait before we crash it closed
     * @param sleepTime how long to wait in milliseconds between loops
     */
    @Override
    public void stop(int count, int sleepTime) {
        String timestamp = TimeUtils.getTimestampIsoInstant();
        logger.logDebug(() ->  String.format("%s Stopping queue %s", timestamp, this));
        stop = true;
        for (int i = 0; i < count; i++) {
            if (queue.isEmpty()) return;
            logger.logDebug(() ->  String.format("%s Queue not yet empty, has %d elements. waiting...%n",timestamp, queue.size()));
            MyThread.sleep(sleepTime);
        }
        isStoppedStatus = true;
        logger.logDebug(() -> String.format("%s Queue %s has %d elements left but we're done waiting.  Queue toString: %s", timestamp, this, queue.size(), queue));
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

    @Override
    public String toString() {
        return this.name;
    }

    Thread getQueueThread() {
        return queueThread;
    }

    @Override
    public LinkedBlockingQueue<RunnableWithDescription> getQueue() {
        return new LinkedBlockingQueue<>(queue);
    }

    @Override
    public boolean isStopped() {
        return isStoppedStatus;
    }
}
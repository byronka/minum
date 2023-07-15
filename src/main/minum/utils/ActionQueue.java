package minum.utils;

import minum.Constants;
import minum.Context;
import minum.logging.ILogger;
import minum.logging.LoggingLevel;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
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
public class ActionQueue {
    private final String name;
    private final ExecutorService queueExecutor;
    private final LinkedBlockingQueue<RunnableWithDescription<?>> queue;
    private final ILogger logger;
    private boolean stop = false;
    private Thread queueThread;
    private final Constants constants;

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
        context.getActionQueueList().add(this);
        this.constants = context.getConstants();
        this.logger = context.getLogger();
    }

    // Regarding the InfiniteLoopStatement - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @SuppressWarnings("InfiniteLoopStatement")
    public ActionQueue initialize() {
        Runnable queueThread = () -> {
            Thread.currentThread().setName(name);
            this.queueThread = Thread.currentThread();
            try {
                while (true) {
                    ThrowingRunnable<?> action = queue.take();
                    try {
                        action.run();
                    } catch (Throwable e) {
                        logger.logAsyncError(() -> e.getMessage());
                    }
                }
            } catch (InterruptedException ex) {
            /*
            this is what we expect to happen.
            once this happens, we just continue on.
            this only gets called when we are trying to shut everything
            down cleanly
             */
                if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.printf(TimeUtils.getTimestampIsoInstant() + " ActionQueue for %s is stopped.%n", name);
            } catch (Exception ex) {
                System.out.printf(TimeUtils.getTimestampIsoInstant() + " ERROR: ActionQueue for %s has stopped unexpectedly. error: %s%n", name, ex);
                throw ex;
            }
        };
        queueExecutor.submit(queueThread);
        return this;
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
     *             return null;
     *         });}
     * </pre>
     * </p>
     * @param action an action to take with no return value.  (this
     *               uses {@link Callable} so we can collect exceptions). Note
     *               that because we are using Callable, it is necessary
     *               to "return null" at the end of the action.

     */
    public void enqueue(String description, ThrowingRunnable<?> action) {
        if (! stop) {
            queue.add(new RunnableWithDescription<>(action, description));
        }
    }

    /**
     * Stops the action queue
     * @param count how many loops to wait before we crash it closed
     * @param sleepTime how long to wait in milliseconds between loops
     */
    public void stop(int count, int sleepTime) {
        String timestamp = TimeUtils.getTimestampIsoInstant();
        if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(timestamp + " Stopping queue " + this);
        stop = true;
        for (int i = 0; i < count; i++) {
            int size = queue.size();
            if (!(size > 0)) return;
            if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) {
                System.out.printf(timestamp + " Queue not yet empty, has %d elements. waiting...%n", size);
            }
            MyThread.sleep(sleepTime);
        }
        System.out.printf(timestamp + " Queue %s has %d elements left but we're done waiting.  Queue toString: %s", this, queue.size(), queue);
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

    public Thread getQueueThread() {
        return queueThread;
    }

    public LinkedBlockingQueue<RunnableWithDescription<?>> getQueue() {
        return queue;
    }
}
package minum.logging;

import minum.Constants;
import minum.utils.CallableWithDescription;
import minum.utils.MyThread;
import minum.utils.TimeUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class is very similar to {@link minum.utils.ActionQueue} but is
 * focused on Logging.
 * <p>
 *     The only reason it was necessary to create a wholly separate stack for logging
 *     is that logging has so many circular dependencies with the rest of the system,
 *     this was the only way to free ourselves during initialization / shutdown.
 * </p>
 */
public class LoggingActionQueue {
    private final String name;
    private final ExecutorService executorService;
    private final LinkedBlockingQueue<CallableWithDescription> queue;
    private boolean stop = false;
    private Thread queueThread;
    private final Constants constants;

    public LoggingActionQueue(String name, ExecutorService executorService, Constants constants) {
        this.name = name;
        this.executorService = executorService;
        this.queue = new LinkedBlockingQueue<>();
        this.constants = constants;
    }

    // Regarding the InfiniteLoopStatement - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @SuppressWarnings("InfiniteLoopStatement")
    public LoggingActionQueue initialize() {
        Callable<Object> queueThread = () -> {
            Thread.currentThread().setName(name);
            this.queueThread = Thread.currentThread();
            try {
                while (true) {
                    Callable<Void> action = queue.take();
                    action.call();
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
            return null;
        };
        executorService.submit(queueThread);
        return this;
    }

    /**
     * Adds something to the queue to be processed.
     * @param action an action to take with no return value.  (this uses callable so we can collect exceptions)
     */
    public void enqueue(String description, Callable<Void> action) {
        if (! stop) {
            queue.add(new CallableWithDescription(action, description));
        }
    }

    /**
     * Stops the action queue
     * @param count how many loops to wait before we crash it closed
     * @param sleepTime how long to wait in milliseconds between loops
     */
    public void stop(int count, int sleepTime) {
        if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " Stopping queue " + this);
        stop = true;
        for (int i = 0; i < count; i++) {
            int size = queue.size();
            if (!(size > 0)) return;
            if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.printf(TimeUtils.getTimestampIsoInstant() + " Queue not yet empty, has %d elements. waiting...%n", size);
            MyThread.sleep(sleepTime);
        }
        System.out.printf(TimeUtils.getTimestampIsoInstant() + " Queue %s has %d elements left but we're done waiting.  Queue toString: %s", this, queue.size(), queue);
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

    public LinkedBlockingQueue<CallableWithDescription> getQueue() {
        return queue;
    }
}
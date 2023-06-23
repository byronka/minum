package minum.utils;

import java.util.ArrayList;
import java.util.List;
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
    private static final List<ActionQueue> allQueues = new ArrayList<>();
    private final String name;
    private final ExecutorService queueExecutor;
    private final LinkedBlockingQueue<CallableWithDescription> queue;
    private boolean stop = false;
    private Thread queueThread;

    public ActionQueue(String name, ExecutorService queueExecutor) {
        this.name = name;
        this.queueExecutor = queueExecutor;
        this.queue = new LinkedBlockingQueue<>();
        allQueues.add(this);
    }

    // Regarding the InfiniteLoopStatement - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @SuppressWarnings("InfiniteLoopStatement")
    public ActionQueue initialize() {
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
                System.out.printf(TimeUtils.getTimestampIsoInstant() + " ActionQueue for %s is stopped.%n", name);
            } catch (Exception ex) {
                System.out.printf(TimeUtils.getTimestampIsoInstant() + " ERROR: ActionQueue for %s has stopped unexpectedly. error: %s%n", name, ex);
                throw ex;
            }
            return null;
        };
        queueExecutor.submit(queueThread);
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
     * This will prevent any new actions being
     * queued (by setting the stop flag to true and thus
     * causing an exception to be thrown
     * when a call is made to [enqueue]) and will
     * block until the queue is empty.
     */
    public void stop() {
        System.out.println(TimeUtils.getTimestampIsoInstant() + " Stopping queue " + this);
        stop = true;
        for (int i = 0; i < 5; i++) {
            int size = queue.size();
            if (!(size > 0)) return;
            System.out.printf(TimeUtils.getTimestampIsoInstant() + " Queue not yet empty, has %d elements. waiting...%n", size);
            MyThread.sleep(20);
        }
        System.out.printf(TimeUtils.getTimestampIsoInstant() + " Queue %s has %d elements left but we're done waiting.  Queue toString: %s", this, queue.size(), queue);
    }

    public static void killAllQueues() {
        System.out.println(TimeUtils.getTimestampIsoInstant() + " Killing all queue threads");
        for (var aq : allQueues) {
            aq.stop();
            System.out.println(TimeUtils.getTimestampIsoInstant() + " killing " + aq.queueThread);
            if (aq.queueThread != null) {
                aq.queueThread.interrupt();
            }
            // at this point, clear out the queue if anything is left in it.
            // this feels imprecise. TODO: look into this deeply.
            aq.queue.clear();

        }
        allQueues.clear();
    }

    @Override
    public String toString() {
        return this.name;
    }
}
package utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class provides the ability to pop items into
 * a queue thread-safely and know they'll happen later.
 *
 * For example, this is helpful for logging, or passing
 * functions to a database.  It lets us run a bit faster,
 * since the I/O actions are happening on a separate
 * thread and the only time required is passing the
 * function of what we want to run later.
 */
public class ActionQueue {
    private final String name;
    private final ExecutorService queueExecutor;
    private final LinkedBlockingQueue<Runnable> queue;
    private boolean stop = false;

    public ActionQueue(String name, ExecutorService queueExecutor) {
        this.name = name;
        this.queueExecutor = queueExecutor;
        this.queue = new LinkedBlockingQueue<>();
    }

    public ActionQueue initialize() {
        queueExecutor.execute(new Thread(() -> {
            try {
                while (true) {
                    Runnable action = queue.take();
                    action.run();
                }
            } catch (InterruptedException ex) {
                /*
                this is what we expect to happen.
                once this happens, we just continue on.
                this only gets called when we are trying to shut everything
                down cleanly
                 */
                System.out.printf("ActionQueue for %s is stopped.%n", name);
            } catch (Throwable ex) {
                System.out.printf("ERROR: ActionQueue for %s has stopped unexpectedly. error: %s%n", name, ex);
            }
        }));
        return this;
    }

    public void enqueue(Runnable action) {
        if (stop) {
            throw new RuntimeException("Attempting to add an action to a stopping queue");
        } else {
            queue.add(action);
        }
    }

    /**
     * This will prevent any new actions being
     * queued (by causing an exception to be thrown
     * when a call is made to [enqueue] and will
     * wait until the queue is empty, then shutdown
     * the thread
     */
    public void stop() {
        stop = true;
        while (queue.size() > 0) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
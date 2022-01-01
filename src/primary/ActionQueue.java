package primary;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

public class ActionQueue {
    private String name;
    private ExecutorService queueExecutor;
    private LinkedBlockingQueue<Runnable> queue;
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
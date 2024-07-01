package com.renomad.minum.queue;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class tracks the overall state of the {@link ActionQueue}s that
 * are in use throughout the system.  We need one central place to
 * track these, so that at system shutdown we can close them all cleanly.
 * <br>
 * As each ActionQueue gets created, it registers itself here.
 */
public class ActionQueueState {

    private final Queue<AbstractActionQueue> aqQueue;

    public ActionQueueState() {
        aqQueue = new LinkedBlockingQueue<>();
    }

    public String aqQueueAsString() {
        return aqQueue.toString();
    }

    public void offerToQueue(AbstractActionQueue actionQueue) {
        aqQueue.offer(actionQueue);
    }

    public AbstractActionQueue pollFromQueue() {
        return aqQueue.poll();
    }

    public boolean isAqQueueEmpty() {
        return aqQueue.isEmpty();
    }

}

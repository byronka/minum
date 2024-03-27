package com.renomad.minum.utils;

import com.renomad.minum.Context;
import com.renomad.minum.logging.ILogger;

import java.util.Queue;

/**
 * This class exists to properly kill off multiple action queues
 */
public final class ActionQueueKiller {

    private final Context context;
    private final ILogger logger;

    public ActionQueueKiller(Context context) {
        this.context = context;
        this.logger = context.getLogger();
    }

    /**
     * Systematically stops and kills all the action queues that have been
     * instantiated in this call tree.
     */
    public void killAllQueues() {
        killAllQueues(context.getAqQueue());
    }

    void killAllQueues(Queue<AbstractActionQueue> aqQueue) {
        logger.logDebug(() -> TimeUtils.getTimestampIsoInstant() + " Killing all queue threads. ");
        for (AbstractActionQueue aq = aqQueue.poll(); aq != null ; aq = aqQueue.poll()) {
            AbstractActionQueue finalAq = aq;
            finalAq.stop();
            logger.logDebug(() -> TimeUtils.getTimestampIsoInstant() + " killing " + finalAq.getQueueThread());
            if (finalAq.getQueueThread() != null) {
                finalAq.getQueueThread().interrupt();
            }
            finalAq.getQueue().clear();
        }
    }
}

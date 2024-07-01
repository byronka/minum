package com.renomad.minum.queue;

import com.renomad.minum.state.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.TimeUtils;

/**
 * This class exists to properly kill off multiple action queues
 */
public final class ActionQueueKiller {

    private final Context context;
    private final ILogger logger;

    /**
     * If we were interrupted while attempting to cleanly kill the
     * action queues, this will be set true
     */
    private boolean hadToInterrupt;

    public ActionQueueKiller(Context context) {
        this.context = context;
        this.logger = context.getLogger();
        hadToInterrupt = false;
    }

    /**
     * Systematically stops and kills all the action queues that have been
     * instantiated in this call tree.
     */
    public void killAllQueues() {
        logger.logDebug(() -> TimeUtils.getTimestampIsoInstant() + " Killing all queue threads. ");
        for (AbstractActionQueue aq = context.getActionQueueState().pollFromQueue(); aq != null ; aq = context.getActionQueueState().pollFromQueue()) {
            AbstractActionQueue finalAq = aq;
            finalAq.stop();
            logger.logDebug(() -> TimeUtils.getTimestampIsoInstant() + " killing " + ((ActionQueue)finalAq).getQueueThread());
            if (((ActionQueue)finalAq).getQueueThread() != null) {
                hadToInterrupt = true;
                System.out.println("had to interrupt " + finalAq);
                ((ActionQueue)finalAq).getQueueThread().interrupt();
            }
        }
    }

    /**
     * A helpful indicator of whether this object was interrupted while
     * looping through the list of action queues
     * @return true If we were interrupted while attempting to cleanly kill the
     *         action queues
     */
    public boolean hadToInterrupt() {
        return hadToInterrupt;
    }

}

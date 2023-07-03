package minum.utils;

import minum.Constants;
import minum.Context;
import minum.logging.LoggingLevel;

/**
 * This class exists to properly kill off multiple action queues
 */
public class ActionQueueKiller {

    private final Context context;
    private final Constants constants;

    public ActionQueueKiller(Context context) {
        this.context = context;
        this.constants = context.getConstants();
    }

    /**
     * Systematically stops and kills all the action queues that have been
     * instantiated in this call tree.
     */
    public void killAllQueues() {
        if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " Killing all queue threads");
        for (ActionQueue aq : context.getActionQueueList()) {
            aq.stop();
            if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " killing " + aq.getQueueThread());
            if (aq.getQueueThread() != null) {
                aq.getQueueThread().interrupt();
            }
            // at this point, clear out the queue if anything is left in it.
            // this feels imprecise. TODO: look into this deeply.
            aq.getQueue().clear();

        }
        context.getActionQueueList().clear();
    }
}

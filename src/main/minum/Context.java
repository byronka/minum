package minum;

import minum.logging.ILogger;
import minum.logging.LoggingLevel;
import minum.utils.ActionQueue;
import minum.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;


public class Context {

    private ILogger logger;
    private final ExecutorService executorService;
    private final Constants constants;
    private final FullSystem fullSystem;
    private final List<ActionQueue> actionQueueList;

    /**
     * In order to avoid statics or singletons allow certain objects to be widely
     * available, we'll store them in this class.
     * @param executorService the code which controls threads
     * @param constants constants that apply throughout the code, configurable by the
     *                  user in the app.config file.
     * @param fullSystem the code which kicks off many of the core functions of
     *                   the application and maintains oversight on those objects.
     */
    public Context(ExecutorService executorService, Constants constants, FullSystem fullSystem) {
        this.executorService = executorService;
        this.constants = constants;
        this.fullSystem = fullSystem;
        this.actionQueueList = new ArrayList<>();
    }

    public void setLogger(ILogger logger) {
        this.logger = logger;
    }

    public ILogger getLogger() {
        return logger;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public Constants getConstants() {
        return constants;
    }

    public FullSystem getFullSystem() {
        return fullSystem;
    }

    public List<ActionQueue> getActionQueueList() {
        return actionQueueList;
    }


    /**
     * Systematically stops and kills all the action queues that have been
     * instantiated in this call tree.
     */
    public void killAllQueues() {
        if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " Killing all queue threads");
        for (ActionQueue aq : getActionQueueList()) {
            aq.stop();
            if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " killing " + aq.getQueueThread());
            if (aq.getQueueThread() != null) {
                aq.getQueueThread().interrupt();
            }
            // at this point, clear out the queue if anything is left in it.
            // this feels imprecise. TODO: look into this deeply.
            aq.getQueue().clear();

        }
        getActionQueueList().clear();
    }

}

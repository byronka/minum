package minum;

import minum.logging.ILogger;
import minum.logging.LoggingLevel;
import minum.utils.ActionQueue;
import minum.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;


/**
 * Holds important system-wide data and methods.
 * <p>
 *     Creating an instance of this and passing it
 *     around lets us reduce some boilerplate code,
 *     but more importantly, it lets us lower the scope.
 * </p>
 * <p>
 *     Previously, some of this was made available as
 *     static values and methods, which added a bit of
 *     complication when reviewing stack traces, and also
 *     prevented including logging in some areas.
 * </p>
 */
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

}

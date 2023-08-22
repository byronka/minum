package minum;

import minum.database.Db;
import minum.database.DbData;
import minum.logging.ILogger;
import minum.utils.ActionQueue;
import minum.utils.ExtendedExecutor;
import minum.web.FullSystem;

import java.nio.file.Path;
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
    private FullSystem fullSystem;
    private final List<ActionQueue> actionQueueList;

    /**
     * In order to avoid statics or singletons allow certain objects to be widely
     * available, we'll store them in this class.
     */
    public Context() {
        this.constants = new Constants();
        this.executorService = ExtendedExecutor.makeExecutorService(constants);
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

    public void setFullSystem(FullSystem fullSystem) {
        this.fullSystem = fullSystem;
    }

    public FullSystem getFullSystem() {
        return fullSystem;
    }

    public List<ActionQueue> getActionQueueList() {
        return actionQueueList;
    }

    /**
     * This is a helper method to instantiate a {@link Db} class,
     * avoiding the need for a user to provide the root database
     * directory and the context.
     *
     * Since this is a generic method, a bit of care is required when
     * calling.  Try to use a pattern like this:
     * <pre>
     * {@code Db<Photograph> photoDb = wf.getDb("photos");}
     * </pre>
     * @param name the name of this data.  Note that this will be used
     *             as the directory for the data, so use characters your
     *             operating system would allow.
     * @param instance an instance of the {@link DbData} data, preferably
     *                 following a null-object pattern.  For example,
     *                 Photograph.EMPTY.  This is used in the Db code
     *                 to deserialize the data when reading.
     */
    public <T extends DbData<?>> Db<T> getDb(String name, T instance) {
        return new Db<>(Path.of(constants.DB_DIRECTORY, name), this, instance);
    }
}

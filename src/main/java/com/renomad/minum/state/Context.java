package com.renomad.minum.state;

import com.renomad.minum.database.Db;
import com.renomad.minum.database.DbData;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.queue.ActionQueueState;
import com.renomad.minum.web.FullSystem;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;


/**
 * Holds important system-wide data and methods.
 * <p>
 *     Creating an instance of this and passing it
 *     around lets us reduce some boilerplate code,
 *     but more importantly, it lets us lower the scope.
 * </p>
 */
public final class Context {

    public static final Context EMPTY = new Context(null, null);
    private ILogger logger;
    private final ExecutorService executorService;
    private final Constants constants;
    private FullSystem fullSystem;
    private final ActionQueueState actionQueueState;

    public Context(ExecutorService executorService, Constants constants) {
        this.executorService = executorService;
        this.constants = constants;
        actionQueueState = new ActionQueueState();
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

    public ActionQueueState getActionQueueState() {
        return actionQueueState;
    }

    /**
     * This is a helper method to instantiate a {@link Db} class,
     * avoiding the need for a user to provide the root database
     * directory and the context.
     * <p>
     * Since this is a generic method, a bit of care is required when
     * calling.  Try to use a pattern like this:
     * <pre>
     * {@code Db<Photograph> photoDb = context.getDb("photos");}
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
        return new Db<>(Path.of(constants.dbDirectory, name), this, instance);
    }
}

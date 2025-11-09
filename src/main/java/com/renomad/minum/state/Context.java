package com.renomad.minum.state;

import com.renomad.minum.database.Db;
import com.renomad.minum.database.DbData;
import com.renomad.minum.database.DbEngine2;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.queue.ActionQueueState;
import com.renomad.minum.web.FullSystem;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;


/**
 * Holds important system-wide data and methods, such as the
 * logger, constants, and the {@link FullSystem} instance.
 * <p>
 *     The common situations:
 * </p>
 * <ul>
 *     <li>Building a Minum {@link Db} database</li>
 *     <li>Getting system constants like the database directory</li>
 *     <li>Getting the system {@link ExecutorService} for starting threads or an {@link com.renomad.minum.queue.ActionQueue}</li>
 *     <li>Getting a {@link FullSystem} object, which has</li>
 *     <ul>
 *         <li>the {@link com.renomad.minum.web.WebFramework}, which registers endpoints</li>
 *         <li>the {@link com.renomad.minum.security.TheBrig}, which handles bad actors on the internet</li>
 *     </ul>
 * </ul>
 */
public final class Context {

    private final ILogger logger;
    private final ExecutorService executorService;
    private final Constants constants;
    private FullSystem fullSystem;
    private final ActionQueueState actionQueueState;

    public Context(ExecutorService executorService, Constants constants, ILogger logger) {
        this.executorService = executorService;
        this.constants = constants;
        actionQueueState = new ActionQueueState();
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
     * calling.  Try to use a pattern like the following pseudocode:
     * {@snippet :
     *  Db<Photograph> photoDb = context.getDb("photos", new Photograph());
     * }
     * @param name the name of this data.  Note that this will be used
     *             as the directory for the data, so use characters your
     *             operating system would allow.
     * @param instance an instance of the {@link DbData} data. This is used in the
     *                 Db code to deserialize the data when reading.
     */
    public <T extends DbData<?>> Db<T> getDb(String name, T instance) {
        return new Db<>(Path.of(constants.dbDirectory, name), this, instance);
    }

    /**
     * This is a helper method to instantiate a {@link DbEngine2} class,
     * using the engine2 database implementation. It is similar to
     * {@link #getDb(String, DbData)} in all other respects.
     * <p>
     *     By switching your old database calls to use this, when it runs
     *     it will convert the file schema.
     * </p>
     * <p>
     *     <b>Please backup your database before conversion</b>
     * </p>
     */
    public <T extends DbData<?>> DbEngine2<T> getDb2(String name, T instance) {
        return new DbEngine2<>(Path.of(constants.dbDirectory, name), this, instance);
    }


}

package com.renomad.minum;

import com.renomad.minum.database.Db;
import com.renomad.minum.database.DbData;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.AbstractActionQueue;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.web.FullSystem;
import com.renomad.minum.web.IInputStreamUtils;

import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;


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
public final class Context {

    public static final Context EMPTY = new Context();
    private IInputStreamUtils inputStreamUtils;
    private FileUtils fileUtils;
    private ILogger logger;
    private ExecutorService executorService;
    private Constants constants;
    private FullSystem fullSystem;
    private final Queue<AbstractActionQueue> aqQueue;

    /**
     * In order to avoid statics or singletons allow certain objects to be widely
     * available, we'll store them in this class.
     */
    public Context() {
        this.aqQueue = new LinkedBlockingQueue<>();
    }

    public void setInputStreamUtils(IInputStreamUtils inputStreamUtils) {
        this.inputStreamUtils = inputStreamUtils;
    }

    public IInputStreamUtils getInputStreamUtils() {
        return inputStreamUtils;
    }

    public FileUtils getFileUtils() {
        return fileUtils;
    }

    public void setFileUtils(FileUtils fileUtils) {
        this.fileUtils = fileUtils;
    }

    public void setLogger(ILogger logger) {
        this.logger = logger;
    }

    public ILogger getLogger() {
        return logger;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setConstants(Constants constants) {
        this.constants = constants;
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

    public Queue<AbstractActionQueue> getAqQueue() {
            return aqQueue;
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

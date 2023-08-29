package com.renomad.minum.logging;

import com.renomad.minum.Constants;
import com.renomad.minum.web.FullSystem;

import java.util.concurrent.ExecutorService;


/**
 * Holds important system-wide data and methods for logging
 * <p>
 *     Logging is special because it requires a lot of stuff
 *     like {@link ExecutorService} and {@link Constants} *before*
 *     they are needed by {@link FullSystem}.  The only way I can
 *     see to avoid circular dependencies is to let the logger
 *     have its own values, independent of the rest of the system.
 * </p>
 */
public final class LoggingContext {

    private ILogger logger;
    private final ExecutorService executorService;
    private final Constants constants;

    /**
     * In order to avoid statics or singletons allow certain objects to be widely
     * available, we'll store them in this class.
     * @param executorService the code which controls threads
     * @param constants constants that apply throughout the code, configurable by the
     *                  user in the minum.config file.
     */
    public LoggingContext(ExecutorService executorService, Constants constants) {
        this.executorService = executorService;
        this.constants = constants;
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

}

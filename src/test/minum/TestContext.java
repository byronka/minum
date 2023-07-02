package minum;

import minum.testing.TestLogger;
import minum.utils.StringUtils;
import minum.web.InputStreamUtils;

import java.util.concurrent.ExecutorService;

/**
 * Similar to its brother {@link Context} but focused on tests
 */
public class TestContext extends Context {

    private TestLogger logger;
    private final ExecutorService executorService;
    private final Constants constants;
    private final StringUtils stringUtils;
    private final InputStreamUtils inputStreamUtils;

    /**
     * In order to avoid statics or singletons allow certain objects to be widely
     * available, we'll store them in this class.
     * @param executorService the code which controls threads
     * @param constants constants that apply throughout the code, configurable by the
     *                  user in the app.config file.
     */
    public TestContext(ExecutorService executorService, Constants constants) {
        super(executorService, constants, null);
        this.executorService = executorService;
        this.constants = constants;
        this.stringUtils = new StringUtils(this);
        this.inputStreamUtils = new InputStreamUtils(this, stringUtils);
    }


    public TestLogger getLogger() {
        return logger;
    }

    public void setLogger(TestLogger logger) {
        this.logger = logger;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public Constants getConstants() {
        return constants;
    }

    public StringUtils getStringUtils() {
        return stringUtils;
    }

    public InputStreamUtils getInputStreamUtils() {
        return inputStreamUtils;
    }
}

package minum;

import minum.logging.ILogger;
import minum.security.TheBrig;
import minum.web.Server;
import minum.web.WebFramework;

import java.util.concurrent.ExecutorService;

/**
 * A fake version of the full system for using in testing,
 * in cases where a full system is not warranted.
 */
public class FakeFullSystem implements IFullSystem {
    @Override
    public void removeShutdownHook() {

    }

    @Override
    public ExecutorService getExecutorService() {
        return null;
    }

    @Override
    public Server getServer() {
        return null;
    }

    @Override
    public Server getSslServer() {
        return null;
    }

    @Override
    public WebFramework getWebFramework() {
        return null;
    }

    @Override
    public TheBrig getTheBrig() {
        return null;
    }

    @Override
    public ILogger getLogger() {
        return null;
    }

    @Override
    public Thread getShutdownHook() {
        return null;
    }

    @Override
    public Context getContext() {
        return null;
    }

    @Override
    public void close() {

    }
}

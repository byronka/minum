package minum;

import minum.logging.ILogger;
import minum.security.TheBrig;
import minum.web.Server;
import minum.web.WebFramework;

import java.util.concurrent.ExecutorService;

public interface IFullSystem {
    void removeShutdownHook();

    ExecutorService getExecutorService();

    Server getServer();

    Server getSslServer();

    WebFramework getWebFramework();

    TheBrig getTheBrig();

    ILogger getLogger();

    Thread getShutdownHook();

    Context getContext();

    void close();
}

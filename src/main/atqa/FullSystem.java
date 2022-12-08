package atqa;

import atqa.logging.ILogger;
import atqa.logging.Logger;
import atqa.utils.ThrowingRunnable;
import atqa.web.*;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import static atqa.DomainRegistry.registerDomains;

/**
 * This class is responsible for kicking off the entire system.
 */
public class FullSystem {

    ILogger logger;
    Server server;
    Server sslServer;

    ExecutorService es;

    public FullSystem(ExecutorService es) {
        this.logger = new Logger(es);
        this.es = es;
    }

    public FullSystem start() throws IOException  {
        WebEngine webEngine = new WebEngine(logger);
        StaticFilesCache sfc = new StaticFilesCache(logger).loadStaticFiles();
        WebFramework wf = new WebFramework(logger);
        addShutdownHook();
        wf.registerStaticFiles(sfc);
        registerDomains(wf, es, logger);
        final var webHandler = wf.makeHandler();
        server = webEngine.startServer(es, webHandler);
        sslServer = webEngine.startSslServer(es, webHandler);
        return this;
    }

    /**
     * this adds a hook to the Java runtime, so that if the app is running
     * and a user stops it - by pressing ctrl+c or a unix "kill" command - the
     * server socket will be shutdown and some messages about closing the server
     * will log
     */
    void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(ThrowingRunnable.throwingRunnableWrapper(this::shutdown)));
    }

    /**
     * Systematically shuts down everything in the system,
     */
    public void shutdown() throws IOException {
        logger.logImperative("Received shutdown command");

        logger.logImperative("Stopping the server");
        server.stop();

        logger.logImperative("Stopping the SSL server");
        sslServer.stop();

        logger.logImperative("Goodbye world!");
    }
}

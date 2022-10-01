package atqa;

import atqa.logging.ILogger;
import atqa.sampledomain.SampleDomain;
import atqa.utils.ThrowingRunnable;
import atqa.web.*;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class FullSystem {

    ILogger logger;
    Server server;
    ExecutorService es;

    public FullSystem(ILogger logger, ExecutorService es) {
        this.logger = logger;
        this.es = es;
    }

    public FullSystem start() throws IOException {
        Web web = new Web(logger);
        StaticFilesCache sfc = new StaticFilesCache(logger).loadStaticFiles();
        WebFramework wf = new WebFramework(logger);

        final var sd = new SampleDomain(es, logger);

        addShutdownHook();


        wf.registerStaticFiles(sfc);
        wf.registerPath(StartLine.Verb.GET, "formentry", sd::formEntry);
        wf.registerPath(StartLine.Verb.POST, "testform", sd::testform);
        wf.registerPath(StartLine.Verb.GET, "shownames", sd::showNames);

        server = web.startServer(es, wf.makeHandler());
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

        logger.logImperative("Shutting down logging");
        logger.stop();

        logger.logImperative("Goodbye world!");
        es.shutdownNow();
    }
}

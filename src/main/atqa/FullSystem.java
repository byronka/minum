package atqa;

import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.ILogger;
import atqa.sampledomain.PersonName;
import atqa.sampledomain.SampleDomain;
import atqa.utils.ThrowingRunnable;
import atqa.web.*;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * This class is responsible for kicking off the entire system.
 */
public class FullSystem {

    ILogger logger;
    Server server;
    Server sslServer;

    ExecutorService es;

    public FullSystem(ILogger logger, ExecutorService es) {
        this.logger = logger;
        this.es = es;
    }

    public FullSystem start() throws IOException  {
        WebEngine webEngine = new WebEngine(logger);
        StaticFilesCache sfc = new StaticFilesCache(logger).loadStaticFiles();
        WebFramework wf = new WebFramework(logger);

        final var sampleDomainDdps = new DatabaseDiskPersistenceSimpler<PersonName>("out/simple_db/names", es, logger);
        final var sd = new SampleDomain(sampleDomainDdps);

        addShutdownHook();


        wf.registerStaticFiles(sfc);
        wf.registerPath(StartLine.Verb.GET, "", WebFramework.redirectTo("index.html"));
        wf.registerPath(StartLine.Verb.GET, "formentry", sd::formEntry);
        wf.registerPath(StartLine.Verb.POST, "testform", sd::testform);
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

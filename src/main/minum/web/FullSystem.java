package minum.web;

import minum.Constants;
import minum.Context;
import minum.logging.ILogger;
import minum.logging.Logger;
import minum.logging.LoggingLevel;
import minum.logging.TestLogger;
import minum.security.TheBrig;
import minum.utils.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * This class is responsible for kicking off the entire system.
 * In particular, look at {@link #start()}
 */
public final class FullSystem implements AutoCloseable {

    final ILogger logger;
    private final Constants constants;
    private Server server;
    private WebFramework webFramework;
    private Server sslServer;
    Thread shutdownHook;
    private TheBrig theBrig;
    final ExecutorService es;
    final InputStreamUtils inputStreamUtils;
    private WebEngine webEngine;

    /**
     * This flag gives us some control if we need
     * to call {@link #close()} manually, so close()
     * doesn't get run again when the shutdownHook
     * tries calling it.  This is primarily an issue just during
     * testing.
     */
    private boolean hasShutdown;

    private final Context context;

    /**
     * This constructor will also build a logger for you, or you
     * can provide a logger.
     * <p>
     *     There may be some redundancy here, but the logger is special.
     *     It's meant to be adjustable and lives before and after most
     *     of the classes, including the FullSystem class.
     * </p>
     * <p>
     *     For that reason, it's good to think of the logger as needing
     *     its own set of special objects, separate from the rest of the system.
     *     Its own executor service, its own constants. What have you.
     * </p>
     */
    public FullSystem(Context context) {
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        this.es = context.getExecutorService();
        this.inputStreamUtils = context.getInputStreamUtils();
        this.context = context;
        context.setFullSystem(this);
    }


    /**
     * Builds a context object that is appropriate as a
     * parameter to constructing a {@link FullSystem}
     */
    public static Context initializeContext() {
        var constants = new Constants();
        var executorService = ExtendedExecutor.makeExecutorService(constants);
        var logger = new Logger(constants, executorService, "primary logger");
        var fileUtils = new FileUtils(logger, constants);
        var inputStreamUtils = new InputStreamUtils(logger, constants);

        var context = new Context();

        context.setConstants(constants);
        context.setExecutorService(executorService);
        context.setLogger(logger);
        context.setFileUtils(fileUtils);
        context.setInputStreamUtils(inputStreamUtils);

        return context;

    }

    /**
     * Kicks off the various bits and pieces.
     * 
     * <p>
     *     There's a number of components to be built and run
     *     to get this application up and running.  Feel free
     *     to peruse this method's code to get a sense of it.
     * </p>
     * 
     */
    public FullSystem start() throws IOException  {
        // create a file in our current working directory to indicate we are running
        createSystemRunningMarker();

        // set up an action to take place if the user shuts us down
        addShutdownHook();
        
        // Add useful startup info to the logs
        String serverComment = "at http://" + constants.HOST_NAME + ":" + constants.SERVER_PORT + " and https://" + constants.HOST_NAME + ":" + constants.SECURE_SERVER_PORT;
        System.out.println(TimeUtils.getTimestampIsoInstant() + " " + " *** Minum is starting "+serverComment+" ***");
        
        // instantiate our security code
        theBrig = new TheBrig(context).initialize();
        
        // the web framework handles the HTTP communications
        webFramework = new WebFramework(context);

        // build the primary http handler - the beating heart of code
        // that handles HTTP protocol
        final var webHandler = webFramework.makePrimaryHttpHandler();

        // should we redirect all insecure traffic to https? If so,
        // then for port 80 all requests will cause a redirect to the secure TLS endpoint
        boolean shouldRedirect = constants.REDIRECT_TO_SECURE;
        var nonTlsWebHandler = shouldRedirect ? webFramework.makeRedirectHandler() : webHandler;
        
        // kick off the servers - low level internet handlers
        webEngine = new WebEngine(context);
        server = webEngine.startServer(es, nonTlsWebHandler);
        sslServer = webEngine.startSslServer(es, webHandler);

        // document how long to start up the system
        var now = ZonedDateTime.now(ZoneId.of("UTC"));
        var formattedNow = now.format(DateTimeFormatter.ISO_INSTANT);
        var nowMillis = now.toInstant().toEpochMilli();
        var startupTime = nowMillis - constants.START_TIME;
        System.out.println(formattedNow + " *** Minum has finished primary startup after " + startupTime + " milliseconds ***");

        return this;
    }

    /**
     * this adds a hook to the Java runtime, so that if the app is running
     * and a user stops it - by pressing ctrl+c or a unix "kill" command - the
     * server socket will be shutdown and some messages about closing the server
     * will log
     */
    private void addShutdownHook() {
        shutdownHook = new Thread(ThrowingRunnable.throwingRunnableWrapper(this::close, logger));
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * this saves a file to the home directory, SYSTEM_RUNNING,
     * that will indicate the system is active
     */
    private void createSystemRunningMarker() throws IOException {
        Files.writeString(Path.of("SYSTEM_RUNNING"), "This file serves as a marker to indicate the system is running.\n");
        new File("SYSTEM_RUNNING").deleteOnExit();
    }

    Server getServer() {
        return server;
    }

    Server getSslServer() {
        return sslServer;
    }

    public WebFramework getWebFramework() {
        return webFramework;
    }

    public TheBrig getTheBrig() {
        return theBrig;
    }

    public Context getContext() {
        return context;
    }

    WebEngine getWebEngine() {
        return webEngine;
    }

    public void close() {

        if (!hasShutdown) {
            logger.logTrace(() -> "close called on " + this);
            try {
                if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG))
                    System.out.println(TimeUtils.getTimestampIsoInstant() + " Received shutdown command");

                if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG))
                    System.out.println(TimeUtils.getTimestampIsoInstant() + " Stopping the server: " + this.server);
                if (server != null) server.close();

                if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG))
                    System.out.println(TimeUtils.getTimestampIsoInstant() + " Stopping the SSL server: " + this.sslServer);
                if (sslServer != null) sslServer.close();

                if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG))
                    System.out.println(TimeUtils.getTimestampIsoInstant() + " Killing all the action queues: " + this.context.getActionQueueList());
                new ActionQueueKiller(context).killAllQueues();

                if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG))
                    System.out.printf(TimeUtils.getTimestampIsoInstant() + " %s says: Goodbye world!%n", this);

            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                hasShutdown = true;
            }
        }
    }

    /**
     * A blocking call for our multi-threaded application.
     * <p>
     * This method is needed because the entire application is
     * multi-threaded.  Let me help contextualize the problem
     * for you:
     * </p>
     * <p>
     *     For this application, multi-threaded means that we
     *     are wrapping our code in {@link Thread} classes and
     *     having them run using a {@link ExecutorService}.  It's
     *     sort of like giving instructions to someone else to carry
     *     out the work and sending them away, trusting the work will
     *     get done, rather than doing it yourself.
     * </p>
     * <p>
     *     But, since our entire system is done this way, once we
     *     have sent all our threads on their way, there's nothing
     *     left for us to do! Continuing the analogy, it is like
     *     our whole job is to give other people instructions, and
     *     then just wait for them to return.
     * </p>
     * <p>
     *     That's the purpose of this method.  It's to wait for
     *     the return.
     * </p>
     * <p>
     *     It's probably best to call this method as one of the
     *     last statements in the main method, so it is clear where
     *     execution is blocking.
     * </p>
     */
    public void block() {
        try {
            this.server.getCentralLoopFuture().get();
            this.sslServer.getCentralLoopFuture().get();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}

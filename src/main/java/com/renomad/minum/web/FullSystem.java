package com.renomad.minum.web;

import com.renomad.minum.queue.ActionQueueKiller;
import com.renomad.minum.state.Constants;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.logging.Logger;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.security.TheBrig;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.*;

import java.io.File;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class is responsible for instantiating necessary classes
 * for a valid system, in the proper order.
 * @see #initialize()
 * @see #start()
 */
public final class FullSystem {

    final ILogger logger;
    private final Constants constants;
    private final FileUtils fileUtils;
    private IServer server;
    private WebFramework webFramework;
    private IServer sslServer;
    Thread shutdownHook;
    private ITheBrig theBrig;
    final ExecutorService es;
    private WebEngine webEngine;

    /**
     * This flag gives us some control if we need
     * to call {@link #shutdown()} manually, so close()
     * doesn't get run again when the shutdownHook
     * tries calling it.  This is primarily an issue just during
     * testing.
     */
    private boolean hasShutdown;

    private final Context context;

    /**
     * This constructor requires a {@link Context} object,
     * but it is easier and recommended to use {@link #initialize()}
     * instead.
     */
    public FullSystem(Context context) {
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        this.fileUtils = new FileUtils(logger, constants);
        this.es = context.getExecutorService();
        this.context = context;
        context.setFullSystem(this);
    }

    /**
     * Builds a context object that is appropriate as a
     * parameter to constructing a {@link FullSystem}
     */
    public static Context buildContext() {
        var constants = new Constants();
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var logger = new Logger(constants, executorService, "primary logger");

        var context = new Context(executorService, constants);
        context.setLogger(logger);

        return context;
    }

    /**
     * This is the typical entry point for system instantiation.  It will build
     * a {@link Context} object for you, and then properly instantiates the {@link FullSystem}.
     * <p>
     *     <em>Here is an example of a simple Main file using this method:</em>
     * </p>
     * <pre>{@code
     *   package org.example;
     *
     *   import com.renomad.minum.web.FullSystem;
     *   import com.renomad.minum.web.Response;
     *
     *   import static com.renomad.minum.web.RequestLine.Method.GET;
     *
     *   public class Main {
     *
     *       public static void main(String[] args) {
     *           FullSystem fs = FullSystem.initialize();
     *           fs.getWebFramework().registerPath(GET, "", request -> Response.htmlOk("<p>Hi there world!</p>"));
     *           fs.block();
     *       }
     *   }
     * }</pre>
     */
    public static FullSystem initialize() {
        var context = buildContext();
        var fullSystem = new FullSystem(context);
        return fullSystem.start();
    }

    /**
     * This method runs the necessary methods for starting the Minum
     * web server.  It is unlikely you will want to use this, unless you
     * require it for more control in testing.
     * @see #initialize()
     */
    public FullSystem start() {
        // create a file in our current working directory to indicate we are running
        createSystemRunningMarker();

        // set up an action to take place if the user shuts us down
        addShutdownHook();
        
        // Add useful startup info to the logs
        String serverComment = "at http://" + constants.hostName + ":" + constants.serverPort + " and https://" + constants.hostName + ":" + constants.secureServerPort;
        logger.logDebug(() -> " *** Minum is starting "+serverComment+" ***");
        
        // instantiate our security code
        theBrig = new TheBrig(context).initialize();
        
        // the web framework handles the HTTP communications
        webFramework = new WebFramework(context);

        // kick off the servers - low level internet handlers
        webEngine = new WebEngine(context, webFramework);
        server = webEngine.startServer();
        sslServer = webEngine.startSslServer();

        // document how long it took to start up the system
        var now = ZonedDateTime.now(ZoneId.of("UTC"));
        var nowMillis = now.toInstant().toEpochMilli();
        var startupTime = nowMillis - constants.startTime;
        logger.logDebug(() -> " *** Minum has finished primary startup after " + startupTime + " milliseconds ***");

        return this;
    }

    /**
     * this adds a hook to the Java runtime, so that if the app is running
     * and a user stops it - by pressing ctrl+c or a unix "kill" command - the
     * server socket will be shutdown and some messages about closing the server
     * will log
     */
    private void addShutdownHook() {
        shutdownHook = new Thread(ThrowingRunnable.throwingRunnableWrapper(this::shutdown, logger));
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * this saves a file to the home directory, SYSTEM_RUNNING,
     * that will indicate the system is active
     */
    private void createSystemRunningMarker() {
        fileUtils.writeString(Path.of("SYSTEM_RUNNING"), "This file serves as a marker to indicate the system is running.\n");
        new File("SYSTEM_RUNNING").deleteOnExit();
    }

    IServer getServer() {
        return server;
    }

    IServer getSslServer() {
        return sslServer;
    }

    public WebFramework getWebFramework() {
        return webFramework;
    }

    public ITheBrig getTheBrig() {
        return theBrig;
    }

    public Context getContext() {
        return context;
    }

    WebEngine getWebEngine() {
        return webEngine;
    }

    public void shutdown() {

        if (!hasShutdown) {
            logger.logTrace(() -> "close called on " + this);
            closeCore(logger, context, server, sslServer, this.toString());
            hasShutdown = true;
        }
    }

    /**
     * The core code for closing resources
     * @param fullSystemName the name of this FullSystem, in cases where several are running concurrently
     */
    static void closeCore(ILogger logger, Context context, IServer server, IServer sslServer, String fullSystemName) {
        try {
            logger.logDebug(() -> "Received shutdown command");
            logger.logDebug(() -> " Stopping the server: " + server);
            server.close();
            logger.logDebug(() -> " Stopping the SSL server: " + server);
            sslServer.close();
            logger.logDebug(() -> "Killing all the action queues: " + context.getActionQueueState().aqQueueAsString());
            new ActionQueueKiller(context).killAllQueues();
            logger.logDebug(() -> String.format(
                    "%s %s says: Goodbye world!%n", TimeUtils.getTimestampIsoInstant(), fullSystemName));
        } catch (Exception e) {
            throw new WebServerException(e);
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
        blockCore(this.server, this.sslServer);
    }

    static void blockCore(IServer server, IServer sslServer) {
        try {
            server.getCentralLoopFuture().get();
            sslServer.getCentralLoopFuture().get();
        } catch (InterruptedException | ExecutionException | CancellationException ex) {
            Thread.currentThread().interrupt();
            throw new WebServerException(ex);
        }
    }

    /**
     * Intentionally return just the default object toString, this is only used
     * to differentiate between multiple instances in memory.
     */
    @Override
    public String toString() {
        return super.toString();
    }
}

package minum;

import minum.logging.ILogger;
import minum.logging.Logger;
import minum.logging.LoggingLevel;
import minum.security.TheBrig;
import minum.utils.*;
import minum.web.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;

import static minum.web.WebEngine.HTTP_CRLF;

/**
 * This class is responsible for kicking off the entire system.
 * In particular, look at {@link #start()}
 */
public class FullSystem implements AutoCloseable {

    final ILogger logger;
    private final Constants constants;
    private Server server;
    private WebFramework webFramework;
    private Server sslServer;
    Thread shutdownHook;
    private TheBrig theBrig;
    final ExecutorService es;
    final InputStreamUtils inputStreamUtils;

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
    public FullSystem(ExecutorService es, Constants constants, ILogger logger) {
        this.es = es;
        this.constants = constants;
        this.context = new Context(es, constants, this);
        this.context.setLogger(logger);
        this.logger = logger;
        this.inputStreamUtils = new InputStreamUtils(context);
    }


    /**
     * This is the expected entry-point for most users.
     */
    public static FullSystem initialize(ILogger logger, Constants constants) {
        final var es = ExtendedExecutor.makeExecutorService(constants);
        try {
            return new FullSystem(es, constants, logger).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Kicks off the various bits and pieces.
     */
    public FullSystem start() throws IOException  {
        createSystemRunningMarker();
        String serverComment = "at http://" + constants.HOST_NAME + ":" + constants.SERVER_PORT + " and https://" + constants.HOST_NAME + ":" + constants.SECURE_SERVER_PORT;
        System.out.println(TimeUtils.getTimestampIsoInstant() + " " + " *** Minum is starting "+serverComment+" ***");
        theBrig = new TheBrig(context).initialize();
        WebEngine webEngine = new WebEngine(context);
        StaticFilesCache sfc = new StaticFilesCache(logger).loadStaticFiles();
        webFramework = new WebFramework(context);
        addShutdownHook();
        webFramework.registerStaticFiles(sfc);
        final var webHandler = webFramework.makePrimaryHttpHandler();
        // should we redirect all insecure traffic to https?
        boolean shouldRedirect = constants.REDIRECT_TO_SECURE;
        var handler = shouldRedirect ? makeRedirectHandler() : webHandler;
        server = webEngine.startServer(es, handler);
        sslServer = webEngine.startSslServer(es, webHandler);

        var now = ZonedDateTime.now(ZoneId.of("UTC"));
        var formattedNow = now.format(DateTimeFormatter.ISO_INSTANT);
        var nowMillis = now.toInstant().toEpochMilli();
        var startupTime = nowMillis - constants.START_TIME;
        System.out.println(formattedNow + " *** Minum has finished primary startup after " + startupTime + " milliseconds ***");

        return this;
    }

    /**
     * This handler redirects all traffic to the HTTPS endpoint.
     * <br>
     * It is necessary to extract the target path, but that's all
     * the help we'll give.  We're not going to extract headers or
     * body, we'll just read the start line and then stop reading from them.
     */
    ThrowingConsumer<ISocketWrapper, IOException> makeRedirectHandler() {
        return (sw) -> {
            try (sw) {
                try (InputStream is = sw.getInputStream()) {

                    String rawStartLine = inputStreamUtils.readLine(is);
                 /*
                  if the rawStartLine is null, that means the client stopped talking.
                  See ISocketWrapper.readLine()
                  */
                    if (rawStartLine == null) {
                        return;
                    }

                    var sl = StartLine.EMPTY(context).extractStartLine(rawStartLine);

                    // just ignore all the rest of the incoming lines.  TCP is duplex -
                    // we'll just start talking back the moment we understand the first line.
                    String date = ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.RFC_1123_DATE_TIME);
                    String hostname = constants.HOST_NAME;
                    sw.send(
                            "HTTP/1.1 303 SEE OTHER" + HTTP_CRLF +
                                    "Date: " + date + HTTP_CRLF +
                                    "Server: minum" + HTTP_CRLF +
                                    "Location: https://" + hostname + "/" + sl.getPathDetails().isolatedPath() + HTTP_CRLF +
                                    HTTP_CRLF
                    );
                }
            }
        };
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

    public void removeShutdownHook() {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
    }

    /**
     * this saves a file to the home directory, SYSTEM_RUNNING,
     * that will indicate the system is active
     */
    private void createSystemRunningMarker() throws IOException {
        Files.writeString(Path.of("SYSTEM_RUNNING"), "This file serves as a marker to indicate the system is running.\n");
        new File("SYSTEM_RUNNING").deleteOnExit();
    }

    public ExecutorService getExecutorService() {
        return es;
    }

    public Server getServer() {
        return server;
    }

    public Server getSslServer() {
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

    public void close() {
        logger.logTrace(() -> "close called on " + this);
        try {
            if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " Received shutdown command");

            if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " Stopping the server: " + this.server);
            if (server != null) server.close();

            if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " Stopping the SSL server: " + this.sslServer);
            if (sslServer != null) sslServer.close();

            if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " Killing all the action queues: " + this.context.getActionQueueList());
            new ActionQueueKiller(context).killAllQueues();

            if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.printf(TimeUtils.getTimestampIsoInstant() + " %s says: Goodbye world!%n", this);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}

package minum.web;

import minum.Config;
import minum.Constants;
import minum.logging.ILogger;
import minum.logging.Logger;
import minum.logging.LoggingLevel;
import minum.security.TheBrig;
import minum.utils.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import static minum.web.WebEngine.HTTP_CRLF;

/**
 * This class is responsible for kicking off the entire system.
 * In particular, look at {@link #start()}
 */
public class FullSystem implements AutoCloseable {

    final ILogger logger;
    private Server server;
    private WebFramework webFramework;
    private Server sslServer;
    Thread shutdownHook;
    private TheBrig theBrig;
    private static Properties properties;
    final ExecutorService es;

    /**
     * This constructor is used when you want to provide the
     * {@link ILogger} and {@link ExecutorService}. It's easier
     * if you use {@link #initialize()} since it handles that for you.
     */
    public FullSystem(ILogger logger, ExecutorService es) {
        this.logger = logger;
        this.es = es;
    }

    /**
     * Instantiate a FullSystem with freshly-constructed
     * values for {@link ILogger} and {@link ExecutorService}.
     */
    public static WebFramework initialize() {
        final var es = ExtendedExecutor.makeExecutorService();
        final var logger = new Logger(es);
        try {
            return new FullSystem(logger, es).start().getWebFramework();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This starts the system running,
     * <em>but you should not call this directly!!</em>.
     * Use {@link #initialize()} instead.  This is
     * a method only needed if you need a bit greater control
     * when starting, which is mostly just required for testing.
     */
    public FullSystem start() throws IOException  {
        createSystemRunningMarker();
        String serverComment = "at http://" + Constants.HOST_NAME + ":" + Constants.SERVER_PORT + " and https://" + Constants.HOST_NAME + ":" + Constants.SECURE_SERVER_PORT;
        System.out.print("\n\n" + TimeUtils.getTimestampIsoInstant() + " " + " *** Minum is starting "+serverComment+" ***\n\n");
        theBrig = new TheBrig(es, logger).initialize();
        WebEngine webEngine = new WebEngine(logger, theBrig);
        StaticFilesCache sfc = new StaticFilesCache(logger).loadStaticFiles();
        webFramework = new WebFramework(this);
        addShutdownHook();
        webFramework.registerStaticFiles(sfc);
        final var webHandler = webFramework.makePrimaryHttpHandler();
        // should we redirect all insecure traffic to https?
        boolean shouldRedirect = Constants.REDIRECT_TO_SECURE;
        var handler = shouldRedirect ? makeRedirectHandler() : webHandler;
        server = webEngine.startServer(es, handler);
        sslServer = webEngine.startSslServer(es, webHandler);
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

                    String rawStartLine = InputStreamUtils.readLine(is);
                 /*
                  if the rawStartLine is null, that means the client stopped talking.
                  See ISocketWrapper.readLine()
                  */
                    if (rawStartLine == null) {
                        return;
                    }
                    var sl = StartLine.extractStartLine(rawStartLine);

                    // just ignore all the rest of the incoming lines.  TCP is duplex -
                    // we'll just start talking back the moment we understand the first line.
                    String date = ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.RFC_1123_DATE_TIME);
                    String hostname = Constants.HOST_NAME;
                    sw.send(
                            "HTTP/1.1 303 SEE OTHER" + HTTP_CRLF +
                                    "Date: " + date + HTTP_CRLF +
                                    "Server: minum" + HTTP_CRLF +
                                    "Location: https://" + hostname + "/" + sl.pathDetails().isolatedPath() + HTTP_CRLF +
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
    void addShutdownHook() {
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
    public static void createSystemRunningMarker() throws IOException {
        Files.writeString(Path.of("SYSTEM_RUNNING"), "This file serves as a marker to indicate the system is running.\n");
        new File("SYSTEM_RUNNING").deleteOnExit();
    }

    /**
     * Gets values out of the properties file at app.config
     */
    public static Properties getConfiguredProperties() {
        return getConfiguredProperties(false);
    }

    /**
     * This overload allows you to specify that the contents of the
     * properties file should be shown when it's read.
     */
    public static Properties getConfiguredProperties(boolean showContents) {
        if (properties == null) {
            properties = new Properties();
            String fileName = "app.config";
            try (FileInputStream fis = new FileInputStream(fileName)) {
                System.out.println(TimeUtils.getTimestampIsoInstant() +
                        " found properties file at ./app.config.  Loading properties");
                properties.load(fis);
                if (showContents) {
                    System.out.println("Elements:");
                    for (var e : properties.entrySet()) {
                        System.out.println("key: " + e.getKey() + " value: " + e.getValue());
                    }
                }
            } catch (Exception ex) {
                Config.printConfigError();
            }
        }
        return properties;
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

    public ILogger getLogger() {
        return logger;
    }

    public Thread getShutdownHook() {
        return shutdownHook;
    }

    @Override
    public void close() {
        logger.logTrace(() -> "close called on " + this);
        try {
            if (Constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " Received shutdown command");

            if (Constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " Stopping the server: " + this.server);
            if (server != null) server.close();

            if (Constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " Stopping the SSL server: " + this.sslServer);
            if (sslServer != null) sslServer.close();

            if (Constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.printf(TimeUtils.getTimestampIsoInstant() + " %s says: Goodbye world!%n", this);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}

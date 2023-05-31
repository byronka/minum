package atqa;

import atqa.logging.ILogger;
import atqa.logging.Logger;
import atqa.utils.ExtendedExecutor;
import atqa.utils.ThrowingConsumer;
import atqa.utils.ThrowingRunnable;
import atqa.utils.TimeUtils;
import atqa.web.*;

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

import static atqa.web.WebEngine.HTTP_CRLF;

/**
 * This class is responsible for kicking off the entire system.
 */
public class FullSystem {

    final ILogger logger;
    public Server server;
    public WebFramework webFramework;
    Server sslServer;
    Thread shutdownHook;
    private static Properties properties;

    final ExecutorService es;

    public FullSystem(ILogger logger, ExecutorService es) {
        this.logger = logger;
        this.es = es;
    }

    public static FullSystem initialize() {
        final var es = ExtendedExecutor.makeExecutorService();
        final var logger = new Logger(es);
        return new FullSystem(logger, es);
    }

    public FullSystem start() throws IOException  {
        createSystemRunningMarker();
        System.out.println(TimeUtils.getTimestampIsoInstant() + " " + " *** Atqa is starting ***");
        WebEngine webEngine = new WebEngine(logger);
        StaticFilesCache sfc = new StaticFilesCache(logger).loadStaticFiles();
        Path dbDir = Path.of(FullSystem.getConfiguredProperties().getProperty("dbdir", "out/simple_db/"));
        webFramework = new WebFramework(es, logger, dbDir);
        addShutdownHook();
        webFramework.registerStaticFiles(sfc);
        final var webHandler = webFramework.makePrimaryHttpHandler();
        // should we redirect all insecure traffic to https?
        boolean shouldRedirect = Boolean.parseBoolean(FullSystem.getConfiguredProperties().getProperty("redirect80", "false"));
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
    private ThrowingConsumer<SocketWrapper, IOException> makeRedirectHandler() {
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
                    String hostname = FullSystem.getConfiguredProperties().getProperty("hostname", "no hostname configured. see app.config");
                    sw.send(
                            "HTTP/1.1 303 SEE OTHER" + HTTP_CRLF +
                                    "Date: " + date + HTTP_CRLF +
                                    "Server: atqa" + HTTP_CRLF +
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
        shutdownHook = new Thread(ThrowingRunnable.throwingRunnableWrapper(this::shutdown));
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public void removeShutdownHook() {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
    }

    /**
     * Systematically shuts down everything in the system,
     */
    public void shutdown() throws IOException {
        System.out.println(TimeUtils.getTimestampIsoInstant() + " Received shutdown command");

        System.out.println(TimeUtils.getTimestampIsoInstant() + " Stopping the server: " + this.server);
        if (server != null) server.stop();

        System.out.println(TimeUtils.getTimestampIsoInstant() + " Stopping the SSL server: " + this.sslServer);
        if (sslServer != null) sslServer.stop();

        System.out.printf(TimeUtils.getTimestampIsoInstant() + " %s says: Goodbye world!%n", this);
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

    public static Properties getConfiguredProperties(boolean showContents) {
        if (properties == null) {
            properties = new Properties();
            String fileName = "app.config";
            try (FileInputStream fis = new FileInputStream(fileName)) {
                System.out.println(TimeUtils.getTimestampIsoInstant() + " found properties file at ./app.config.  Loading properties");
                properties.load(fis);
                if (showContents) {
                    System.out.println("Elements:");
                    for (var e : properties.entrySet()) {
                        System.out.println("key: " + e.getKey() + " value: " + e.getValue());
                    }
                }
            } catch (Exception ex) {
                System.out.println(TimeUtils.getTimestampIsoInstant() + " no properties file found at ./app.config.  Instantiating empty properties object");
                return new Properties();
            }
        }
        return properties;
    }

}

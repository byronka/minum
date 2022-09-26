package atqa;

import atqa.logging.ILogger;
import atqa.logging.Logger;
import atqa.utils.ExtendedExecutor;
import atqa.utils.ThrowingRunnable;
import atqa.web.StartLine;
import atqa.web.Web;
import atqa.web.WebFramework;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import static atqa.web.StatusLine.StatusCode._200_OK;

public class Main {

    static ILogger logger;
    static Web.Server server;

    public static void main(String[] args) throws IOException {
        ExecutorService es = ExtendedExecutor.makeExecutorService();
        logger = new Logger(es);
        Web web = new Web(logger);
        WebFramework wf = new WebFramework(logger);
        addShutdownHook();
        wf.registerPath(StartLine.Verb.GET, "add_two_numbers", Main::addTwoNumbers);
        wf.registerPath(StartLine.Verb.GET, "", Main::getIndex);
        server = web.startServer(es, wf.makeHandler());
    }

    static WebFramework.Response addTwoNumbers(WebFramework.Request r) {
        int aValue = Integer.parseInt(r.sl().pathDetails().queryString().get("a"));
        int bValue = Integer.parseInt(r.sl().pathDetails().queryString().get("b"));
        int sum = aValue + bValue;
        String sumString = String.valueOf(sum);
        return new WebFramework.Response(_200_OK, sumString);
    }

    static WebFramework.Response getIndex(WebFramework.Request r) {
        return new WebFramework.Response(_200_OK, "");
    }

    /**
     * this adds a hook to the Java runtime, so that if the app is running
     * and a user stops it - by pressing ctrl+c or a unix "kill" command - the
     * server socket will be shutdown and some messages about closing the server
     * will log
     */
    static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(ThrowingRunnable.throwingRunnableWrapper(() -> shutdown(logger))));
    }


    /**
     * Systematically shuts down everything in the system,
     */
    static void shutdown(ILogger logger) throws IOException {
        logger.logImperative("Received shutdown command");
        logger.logImperative("Stopping the server");
        server.stop();

        logger.logImperative("Shutting down logging");
        logger.stop();

        logger.logImperative("Goodbye world!");
    }
}

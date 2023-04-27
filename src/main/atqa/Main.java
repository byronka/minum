package atqa;

import atqa.logging.Logger;
import atqa.utils.ExtendedExecutor;
import atqa.utils.TimeUtils;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static atqa.FullSystem.createSystemRunningMarker;

public class Main {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        System.out.println(startHeader);
        createSystemRunningMarker();

        final var es = ExtendedExecutor.makeExecutorService();
        final var logger = new Logger(es);
        final var fs = new FullSystem(logger, es).start();
        // Wait until they are all done
        fs.server.centralLoopFuture.get();
        fs.sslServer.centralLoopFuture.get();
    }

    static final String startHeader =
                TimeUtils.getTimestampIsoInstant() + " " + " *** Atqa is starting ***";

}

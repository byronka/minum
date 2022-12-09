package atqa;

import atqa.logging.Logger;
import atqa.utils.ExtendedExecutor;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class Main {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        final var es = ExtendedExecutor.makeExecutorService();
        final var logger = new Logger(es);
        final var fs = new FullSystem(logger, es).start();
        // Wait until they are all done
        fs.server.centralLoopFuture.get();
        fs.sslServer.centralLoopFuture.get();
    }

}

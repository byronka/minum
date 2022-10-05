package atqa;

import atqa.logging.Logger;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        try (final var es = Executors.newVirtualThreadPerTaskExecutor()) {
            final var logger = new Logger(es);
            final var fs = new FullSystem(logger, es).start();
            // Wait until they are all done
            fs.server.centralLoopFuture.get();
            fs.sslServer.centralLoopFuture.get();
        }
    }

}

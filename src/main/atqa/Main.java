package atqa;

import atqa.utils.ExtendedExecutor;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class Main {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        try (final var es = ExtendedExecutor.makeExecutorService()) {
            final var fs = new FullSystem(es).start();
            // Wait until they are all done
            fs.server.centralLoopFuture.get();
            fs.sslServer.centralLoopFuture.get();
        }
    }

}

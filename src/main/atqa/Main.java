package atqa;

import atqa.logging.Logger;
import atqa.utils.ExtendedExecutor;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        final var es = ExtendedExecutor.makeExecutorService();
        final var logger = new Logger(es);
        new FullSystem(logger, es).start();
    }

}

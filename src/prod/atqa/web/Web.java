package atqa.web;

import atqa.logging.ILogger;
import atqa.utils.ThrowingConsumer;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ExecutorService;

/**
 * This class contains the basic internet capabilities
 */
public class Web {

  /**
   * This constructor allows us to set the zoned date/time, to
   * better control the system during testing
   */
  public Web(ILogger logger) {
      this.logger = logger;
      this.logger.logDebug(() -> "Using a supplied logger");
  }

  enum HttpVersion {
    ONE_DOT_ZERO, ONE_DOT_ONE
  }

  private final ILogger logger;
  public static final String HTTP_CRLF = "\r\n";

  public Server startServer(ExecutorService es, ThrowingConsumer<SocketWrapper, IOException> handler) throws IOException {
    int port = 8080;
    ServerSocket ss = new ServerSocket(port);
    logger.logDebug(() -> String.format("Just created a new ServerSocket: %s", ss));
    Server server = new Server(this, ss, logger);
    server.start(es, handler);
    return server;
  }

  /**
   * Create a listening server
   */
  public Server startServer(ExecutorService es) throws IOException {
    return startServer(es, null);
  }

  public SocketWrapper startClient(Server server) throws IOException {
    Socket socket = new Socket(server.getHost(), server.getPort());
    logger.logDebug(() -> String.format("Just created new client socket: %s", socket));
    return new SocketWrapper(socket, logger);
  }

}

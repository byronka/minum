package atqa.web;

import atqa.logging.ILogger;
import atqa.utils.ConcurrentSet;
import atqa.utils.MyThread;
import atqa.utils.ThrowingConsumer;
import atqa.utils.ThrowingRunnable;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static atqa.utils.Invariants.mustBeFalse;

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

  /**
   * The purpose here is to make it marginally easier to
   * work with a ServerSocket.
   *
   * First, instantiate this class using a running serverSocket
   * Then, by running the start method, we gain access to
   * the server's socket.  This way we can easily test / control
   * the server side but also tie it in with an ExecutorService
   * for controlling lots of server threads.
   */
  public class Server implements AutoCloseable{
    private final ServerSocket serverSocket;
    private final SetOfServers setOfServers;

    /**
     * This is the future returned when we submitted the
     * thread for the central server loop to the ExecutorService
     */
    public Future<?> centralLoopFuture;

    public Server(ServerSocket ss) {
      this.serverSocket = ss;
      setOfServers = new SetOfServers(new ConcurrentSet<>(), logger);
    }

    public void start(ExecutorService es, ThrowingConsumer<SocketWrapper, IOException> handler) {
      Runnable t = ThrowingRunnable.throwingRunnableWrapper(() -> {
        try {
          // yes, this infinite loop can only exit by an exception.  But this is
          // the beating heart of a server, and to the best of my current knowledge,
          // when a server socket is force-closed it's going to throw an exception, and
          // that's just part of its life cycle
          //noinspection InfiniteLoopStatement
          while (true) {
            logger.logDebug(() -> "server waiting to accept connection");
            Socket freshSocket = serverSocket.accept();
            SocketWrapper sw = new SocketWrapper(Web.this, freshSocket, setOfServers, logger);
            logger.logDebug(() -> String.format("server accepted connection: remote: %s", sw.getRemoteAddr()));
            setOfServers.add(sw);
            if (handler != null) {
              es.submit(ThrowingRunnable.throwingRunnableWrapper(() -> handler.accept(sw)));
            }
          }
        } catch (SocketException ex) {
          if (!ex.getMessage().contains("Socket closed")) {
            throw new RuntimeException(ex);
          }
        }
      });
      this.centralLoopFuture = es.submit(t);
    }

    public void close() throws IOException {
        serverSocket.close();
    }

    public String getHost() {
      return serverSocket.getInetAddress().getHostAddress();
    }

    public int getPort() {
      return serverSocket.getLocalPort();
    }

    /**
     * This is a helper method to find the server SocketWrapper
     * connected to a client SocketWrapper.
     */
    public SocketWrapper getServer(SocketWrapper sw) {
      return setOfServers.getSocketWrapperByRemoteAddr(sw.getLocalAddr(), sw.getLocalPort());
    }

    public void stop() throws IOException {
      // close all the running sockets
      setOfServers.stopAllServers();

      // close the primary server socket
      serverSocket.close();
    }

  }

  public Web.Server startServer(ExecutorService es, ThrowingConsumer<SocketWrapper, IOException> handler) throws IOException {
    int port = 8080;
    ServerSocket ss = new ServerSocket(port);
    logger.logDebug(() -> String.format("Just created a new ServerSocket: %s", ss));
    Server server = new Server(ss);
    server.start(es, handler);
    return server;
  }

  /**
   * Create a listening server
   */
  public Web.Server startServer(ExecutorService es) throws IOException {
    return startServer(es, null);
  }

  public SocketWrapper startClient(Server server) throws IOException {
    Socket socket = new Socket(server.getHost(), server.getPort());
    logger.logDebug(() -> String.format("Just created new client socket: %s", socket));
    return new SocketWrapper(this, socket, logger);
  }

}

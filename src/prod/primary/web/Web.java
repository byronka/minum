package primary.web;

import logging.ILogger;
import logging.Logger;
import utils.ConcurrentSet;
import utils.MyThread;

import java.io.IOException;
import java.net.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static utils.Invariants.mustBeFalse;

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
  public final String HTTP_CRLF = "\r\n";

  private void addToSetOfServers(ConcurrentSet<SocketWrapper> setOfServers, SocketWrapper sw) {
    setOfServers.add(sw);
    int size = setOfServers.size();
    logger.logDebug(() -> "added " + sw + " to setOfServers. size: " + size);
  }

  private void removeFromSetOfServers(ConcurrentSet<SocketWrapper> setOfServers, SocketWrapper sw) {
    setOfServers.remove(sw);
    int size = setOfServers.size();
    logger.logDebug(() -> "removed " + sw + " from setOfServers. size: " + size);
  }

  public interface ISocketWrapper extends AutoCloseable {
    void send(String msg) throws IOException;

    void sendHttpLine(String msg) throws IOException;

    String readLine() throws IOException;

    String getLocalAddr();

    int getLocalPort();

    SocketAddress getRemoteAddr();

    void close() throws IOException;

    String readByLength(int length) throws IOException;
  }

  /**
   * This wraps Sockets to make them simpler / more particular to our use case
   */
  public class SocketWrapper implements ISocketWrapper {

    private final Socket socket;
    private final OutputStream writer;
    private final BufferedReader reader;
    private ConcurrentSet<SocketWrapper> setOfServers;

    public SocketWrapper(Socket socket) throws IOException {
      this.socket = socket;
      writer = socket.getOutputStream();
      reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public SocketWrapper(Socket socket, ConcurrentSet<SocketWrapper> scs) throws IOException {
      this(socket);
      this.setOfServers = scs;
    }

    @Override
    public void send(String msg) throws IOException {
      writer.write(msg.getBytes());
    }

    @Override
    public void sendHttpLine(String msg) throws IOException {
      logger.logDebug(() -> String.format("socket sending: %s", Logger.showWhiteSpace(msg)));
      send(msg + HTTP_CRLF);
    }

    @Override
    public String readLine() throws IOException {
      return reader.readLine();
    }

    @Override
    public String getLocalAddr() {
      return socket.getLocalAddress().getHostAddress();
    }

    @Override
    public int getLocalPort() {
      return socket.getLocalPort();
    }

    @Override
    public SocketAddress getRemoteAddr() {
      return socket.getRemoteSocketAddress();
    }

    @Override
    public void close() throws IOException {
        socket.close();
        if (setOfServers != null) {
          removeFromSetOfServers(setOfServers, this);
        }
    }

    @Override
    public String readByLength(int length) throws IOException {
      char[] cb = new char[length];
      int countOfBytesRead = reader.read(cb, 0, length);
      mustBeFalse (countOfBytesRead == -1, "end of file hit");
      mustBeFalse (countOfBytesRead != length, String.format("length of bytes read (%d) wasn't equal to what we specified (%d)", countOfBytesRead, length));
      return new String(cb);
    }
  }

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
    private final ConcurrentSet<SocketWrapper> setOfServers;

    /**
     * This is the future returned when we submitted the
     * thread for the central server loop to the ExecutorService
     */
    public Future<?> centralLoopFuture;

    public Server(ServerSocket ss) {
      this.serverSocket = ss;
      setOfServers = new ConcurrentSet<>();
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
            SocketWrapper sw = new SocketWrapper(freshSocket, setOfServers);
            logger.logDebug(() -> String.format("server accepted connection: remote: %s", sw.getRemoteAddr()));
            addToSetOfServers(setOfServers, sw);
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
      return getSocketWrapperByRemoteAddr(sw.getLocalAddr(), sw.getLocalPort());
    }


    /**
     * This is a program used during testing so we can find the server
     * socket that corresponds to a particular client socket.
     *
     * Due to the circumstances of the TCP handshake, there's a bit of
     * time where the server might not have finished initialization,
     * and been put into the list of current server sockets.
     *
     * For that reason, if we come in here and don't find it initially, we'll
     * sleep and then try again, up to three times.
     */
    private SocketWrapper getSocketWrapperByRemoteAddr(String address, int port) {
      int maxLoops = 3;
      for (int loopCount = 0; loopCount < maxLoops; loopCount++ ) {
        List<SocketWrapper> servers = setOfServers
                .asStream()
                .filter((x) -> x.getRemoteAddr().equals(new InetSocketAddress(address, port)))
                .toList();
        mustBeFalse(servers.size() > 1, "Too many sockets found with that address");
        if (servers.size() == 1) {
          return servers.get(0);
        }

        // if we got here, we didn't find a server in the list - probably because the TCP
        // initialization has not completed.  Retry after a bit.  The TCP process is dependent on
        // a whole lot of variables outside our control - downed lines, slow routers, etc.
        //
        // on the other hand, this code should probably only be called in testing, so maybe fewer
        // out-of-bounds problems?
        int finalLoopCount = loopCount;
        logger.logDebug(() -> String.format("no server found, sleeping on it... (attempt %d)", finalLoopCount + 1));
        MyThread.sleep(10);
      }
      throw new RuntimeException("No socket found with that address");
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

  public Web.SocketWrapper startClient(Server server) throws IOException {
    Socket socket = new Socket(server.getHost(), server.getPort());
    logger.logDebug(() -> String.format("Just created new client socket: %s", socket));
    return new SocketWrapper(socket);
  }

}

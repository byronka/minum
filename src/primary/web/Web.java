package primary.web;

import logging.ILogger;
import logging.Logger;
import utils.ConcurrentSet;

import java.io.IOException;
import java.net.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static utils.Invariants.mustBeFalse;

/**
 * This class contains the basic internet capabilities
 */
public class Web {

  public Web() {
    this(null);
  }

  public Web(ILogger logger) {
    this(logger, null);
  }

  /**
   * This constructor allows us to set the zoned date/time, to
   * better control the system during testing
   */
  public Web(ILogger logger, ZonedDateTime zdt) {
    if (logger == null) {
      this.logger = msg -> System.out.println(msg.get());
      this.logger.logDebug(() -> "Using a local logger");
    } else {
      this.logger = logger;
      this.logger.logDebug(() -> "Using a supplied logger");
    }
    this.zdt = zdt;
  }

  enum HttpVersion {
    ONE_DOT_ZERO, ONE_DOT_ONE
  }

  private final ILogger logger;
  public final String HTTP_CRLF = "\r\n";
  private final ZonedDateTime zdt;

  private ZonedDateTime getZonedDateTimeNow() {
    if (zdt != null) {
      return zdt;
    } else {
      return ZonedDateTime.now(ZoneId.of("UTC"));
    }
  }

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
    void send(String msg);

    void sendHttpLine(String msg);

    String readLine();

    String getLocalAddr();

    int getLocalPort();

    SocketAddress getRemoteAddr();

    void close();

    String readByLength(int length);
  }

  /**
   * This wraps Sockets to make them simpler / more particular to our use case
   */
  public class SocketWrapper implements ISocketWrapper {

    private final Socket socket;
    private final OutputStream writer;
    private final BufferedReader reader;
    private ConcurrentSet<SocketWrapper> setOfServers;

    public SocketWrapper(Socket socket) {
      this.socket = socket;
      try {
        writer = socket.getOutputStream();
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    public SocketWrapper(Socket socket, ConcurrentSet<SocketWrapper> scs) {
      this(socket);
      this.setOfServers = scs;
    }

    @Override
    public void send(String msg) {
      try {
        writer.write(msg.getBytes());
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    @Override
    public void sendHttpLine(String msg) {
      logger.logDebug(() -> String.format("socket sending: %s", Logger.showWhiteSpace(msg)));
      send(msg + HTTP_CRLF);
    }

    @Override
    public String readLine() {
      try {
        return reader.readLine();
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
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
    public void close() {
      try {
        socket.close();
        if (setOfServers != null) {

          removeFromSetOfServers(setOfServers, this);
        }
      } catch(Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    @Override
    public String readByLength(int length) {
      char[] cb = new char[length];
      try {
        int countOfBytesRead = reader.read(cb, 0, length);
        mustBeFalse (countOfBytesRead == -1, "end of file hit");
        mustBeFalse (countOfBytesRead != length, String.format("length of bytes read (%d) wasn't equal to what we specified (%d)", countOfBytesRead, length));
        return new String(cb);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
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

    public void start(ExecutorService es, Consumer<SocketWrapper> handler) {
      Thread t = new Thread(() -> {
        try {
          while (true) {
            logger.logDebug(() -> "server waiting to accept connection");
            Socket freshSocket = serverSocket.accept();
            SocketWrapper sw = new SocketWrapper(freshSocket, setOfServers);
            logger.logDebug(() -> String.format("server accepted connection: remote: %s", sw.getRemoteAddr()));
            addToSetOfServers(setOfServers, sw);
            if (handler != null) {
              es.submit(new Thread(() -> handler.accept(sw)));
            }
          }
        } catch (SocketException ex) {
          if (!ex.getMessage().contains("Socket closed")) {
            throw new RuntimeException(ex);
          }
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      });
      this.centralLoopFuture = es.submit(t);
    }

    public void close() {
      try {
        serverSocket.close();
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
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
     * time where the server might still be "figuring things out", and
     * when we come through here the server hasn't yet finally come
     * out of "accept" and been put into the list of current server sockets.
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
        int finalLoopCount = loopCount;
        logger.logDebug(() -> String.format("no server found, sleeping on it... (attempt %d)", finalLoopCount + 1));
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      throw new RuntimeException("No socket found with that address");
    }

  }

  public Web.Server startServer(ExecutorService es, Consumer<SocketWrapper> handler) {
    try {
      int port = 8080;
      ServerSocket ss = new ServerSocket(port);
      logger.logDebug(() -> String.format("Just created a new ServerSocket: %s", ss));
      Server server = new Server(ss);
      server.start(es, handler);
      return server;
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Create a listening server
   */
  public Web.Server startServer(ExecutorService es) {
    return startServer(es, null);
  }

  public Web.SocketWrapper startClient(Server server) {
    try {
      Socket socket = new Socket(server.getHost(), server.getPort());
      logger.logDebug(() -> String.format("Just created new client socket: %s", socket));
      return new SocketWrapper(socket);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * This is the brains of how the server responds to web clients
   */
  public final Consumer<Web.SocketWrapper> serverHandler = (sw) -> {
    StartLine sl = StartLine.extractStartLine(sw.readLine());
    HeaderInformation hi = HeaderInformation.extractHeaderInformation(sw);

    int aValue = Integer.parseInt(sl.getQueryString().get("a"));
    int bValue = Integer.parseInt(sl.getQueryString().get("b"));
    int sum = aValue + bValue;
    String sumString = String.valueOf(sum);

    sw.sendHttpLine("HTTP/1.1 200 OK");
    String date = getZonedDateTimeNow().format(DateTimeFormatter.RFC_1123_DATE_TIME);
    sw.sendHttpLine("Date: " + date);
    sw.sendHttpLine("Server: atqa");
    sw.sendHttpLine("Content-Type: text/plain; charset=UTF-8");
    sw.sendHttpLine("Content-Length: " + sumString.length());
    sw.sendHttpLine("");
    sw.sendHttpLine(sumString);
  };

}

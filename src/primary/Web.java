package primary;

import java.io.IOException;
import java.net.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;

public class Web {

  private ILogger logger;
  public final String HTTP_CRLF = "\r\n";

  private void addToSetOfServers(SimpleConcurrentSet<SocketWrapper> setOfServers, SocketWrapper sw) {
    setOfServers.add(sw);
    int size = setOfServers.size();
    logger.logDebug(() -> "added " + sw + " to setOfServers. size: " + size);
  }

  private void removeFromSetOfServers(SimpleConcurrentSet<SocketWrapper> setOfServers, SocketWrapper sw) {
    setOfServers.remove(sw);
    int size = setOfServers.size();
    logger.logDebug(() -> "removed " + sw + " from setOfServers. size: " + size);
  }

  public Web(ILogger logger) {
    if (logger == null) {
      this.logger = msg -> System.out.println(msg.get());
      this.logger.logDebug(() -> "Using a local logger");
    } else {
      this.logger = logger;
      this.logger.logDebug(() -> "Using a supplied logger");
    }
  }

  /**
   * This wraps Sockets to make them simpler / more particular to our use case
   */
  class SocketWrapper implements AutoCloseable {

    private final Socket socket;
    private final OutputStream writer;
    private final BufferedReader reader;
    private SimpleConcurrentSet<SocketWrapper> setOfServers;

    public SocketWrapper(Socket socket) {
      this.socket = socket;
      try {
        writer = socket.getOutputStream();
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    public SocketWrapper(Socket socket, SimpleConcurrentSet<SocketWrapper> scs) {
      this(socket);
      this.setOfServers = scs;
    }

    public void send(String msg) {
      try {
        writer.write(msg.getBytes());
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    public String readLine() {
      try {
        return reader.readLine();
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    public String getLocalAddr() {
      return socket.getLocalAddress().getHostAddress();
    }

    public int getLocalPort() {
      return socket.getLocalPort();
    }

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
  class Server implements AutoCloseable{
    private final ServerSocket serverSocket;
    private SimpleConcurrentSet<SocketWrapper> setOfServers;

    public Server(ServerSocket ss) {
      this.serverSocket = ss;
      setOfServers = new SimpleConcurrentSet<>();
    }

    public void start(ExecutorService es) {
      Thread t = new Thread(() -> {
        try {
          while (true) {
            logger.logDebug(() -> "server waiting to accept connection");
            SocketWrapper sw = new SocketWrapper(serverSocket.accept(), setOfServers);
            logger.logDebug(() -> String.format("server accepted connection: remote: %s", sw.getRemoteAddr()));
            addToSetOfServers(setOfServers, sw);
          }
        } catch (SocketException ex) {
          if (ex.getMessage().contains("Socket closed")) {
            // just swallow the complaint.  accept always
            // throw this exception when we run close()
            // on the server socket
          } else {
            throw new RuntimeException(ex);
          }
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      });
      es.submit(t);
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
        if (servers.size() > 1) {
          throw new RuntimeException("Too many sockets found with that address");
        } else if (servers.size() == 1) {
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

  /**
   * Create a listening server
   */
  public Web.Server startServer(ExecutorService es) {
    try {
      int port = 8080;
      ServerSocket ss = new ServerSocket(port);
      logger.logDebug(() -> String.format("Just created a new ServerSocket: %s", ss));
      Server server = new Server(ss);
      server.start(es);
      return server;
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
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

}

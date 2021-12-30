package primary;

import java.io.IOException;
import java.net.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.*;

public class Web {

  public final String HTTP_CRLF = "\r\n";

  /**
   * This wraps Sockets to make them simpler / more particular to our use case
   */
  static class SocketWrapper implements AutoCloseable {

    private final Socket socket;
    private final OutputStream writer;
    private final BufferedReader reader;

    public SocketWrapper(Socket socket) {
      this.socket = socket;
      try {
        writer = socket.getOutputStream();
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
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
  static class Server implements AutoCloseable{
    private final ServerSocket serverSocket;
    private List<SocketWrapper> socketWrappers;

    public Server(ServerSocket ss) {
      this.serverSocket = ss;
      socketWrappers = new ArrayList<>();
    }

    public void start() {
      Thread t = new Thread(() -> {
        try {
          while (true) {
            SocketWrapper sw = new SocketWrapper(serverSocket.accept());
            socketWrappers.add(sw);
          }
        } catch (SocketException ex) {
          if (ex.getMessage().contains("Socket closed")) {
            // just swallow the complaint.  accept always
            // throw this exception when we run close()
            // on the server socket
          }
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      });
      t.start();
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

    public SocketWrapper getSocketWrapperByRemoteAddr(String addr, int port) {
      List<SocketWrapper> wrappers = socketWrappers
              .stream()
              .filter((x) -> x.getRemoteAddr().equals(new InetSocketAddress(addr, port)))
              .toList();
      if (wrappers.size() > 1) {
        throw new RuntimeException("Too many sockets found with that address");
      } else if (wrappers.size() == 1) {
        return wrappers.get(0);
      } else {
        throw new RuntimeException("No socket found with that address");
      }
    }

  }

  /**
   * Create a listening server
   */
  public static Web.Server startServer() {
    int port = 8080;
    try {
      ServerSocket ss = new ServerSocket(port);
      Server server = new Server(ss);
      server.start();
      return server;
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static Web.SocketWrapper startClient(Server server) {
    Socket socket;
    try {
      socket = new Socket(server.getHost(), server.getPort());
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    return new SocketWrapper(socket);
  }

}

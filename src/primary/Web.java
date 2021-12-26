package primary;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class Web {

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

    @Override
    public void close() throws Exception {
      socket.close();
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
    private SocketWrapper sw;

    public Server(ServerSocket ss) {
      this.serverSocket = ss;
    }

    public void start() {
      Thread t = new Thread(() -> {
        try {
          sw = new SocketWrapper(serverSocket.accept());
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
      return serverSocket.getInetAddress().getHostName();
    }

    public int getPort() {
      return serverSocket.getLocalPort();
    }

    public void send(String msg) {
      sw.send(msg);
    }

    public String readLine() {
      return sw.readLine();
    }

  }

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

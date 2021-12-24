package primary;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class Web {

  static class Server {
    private String name;
    private ServerSocket serverSocket;
    private Socket socket;
    private int port;
    private OutputStream writer;
    private BufferedReader reader;

    public Server(String name, int port) {
      try {
        this.name = name;
        this.port = port;
        serverSocket = new ServerSocket(port);  
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    public void start() {
      Thread t = new Thread(() -> {
        try {
          socket = serverSocket.accept();
          writer = socket.getOutputStream();
          reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      });
      t.start();
    } 

    public String getHost() {
      return serverSocket.getInetAddress().getHostName();
    }

    public int getPort() {
      return port;
    }

    public void send(String msg) {
      try {
        writer.write(msg.getBytes());
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    public String readLine() {
      try {
        return reader.readLine();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  static class Client {
    private String name;
    private Thread myThread;
    private Socket socket;
    private OutputStream writer;
    private BufferedReader reader;

    public Client(String name, Socket socket) {
      this.name = name;
      this.socket = socket;
      try {
        writer = socket.getOutputStream();
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    public void send(String msg) {
      try {
        writer.write(msg.getBytes());
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    public String readLine() {
      try {
        return reader.readLine();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  public static Web.Server startServer() {
    int port = 8080;
    Server server = new Server("myserver", port);
    server.start();
    return server;
  }

  public static Web.Client startClient(Server server) {
    Socket socket;
    try {
      socket = new Socket(server.getHost(), server.getPort());
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    return new Client("client", socket);
  }

}

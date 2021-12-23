package primary;

public class Web {

  static class Server {
    public String receive() {
      return "foo";
    }
  }

  static class Client {
    public void connectTo(Server server) {
      // do nothing
    }
    public void send(String msg) {
      // do nothing
    }
  }

  public static Web.Server startServerWithWait() {
    return new Server();
  }

  public static Web.Client startClient() {
    return new Client();
  }

}

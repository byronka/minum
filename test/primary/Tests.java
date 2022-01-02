package primary;

import java.util.concurrent.*;

class Tests {

  public static void main(String[] args) {

    ExecutorService es = ExtendedExecutor.makeExecutorService();
    Logger logger = new Logger(es).initialize().turnOff(Logger.Type.DEBUG);
    Web web = new Web(logger);

    try {
      logger.test("a happy path");
      {
        int result = Main.add(2, 3);
        assertEquals(result, 5);
      }

      logger.test("a couple negatives");
      {
        int result = Main.add(-2, -3);
        assertEquals(result, -5);
      }

      logger.test("with zeros");
      {
        int result = Main.add(0, 0);
        assertEquals(result, 0);
      }

      logger.test("client / server");
      {
        try (Web.Server primaryServer = web.startServer(es)) {
          try (Web.SocketWrapper client = web.startClient(primaryServer)) {
            try (Web.SocketWrapper server = primaryServer.getServer(client)) {

              client.send("hello foo!\n");
              String result = server.readLine();
              assertEquals("hello foo!", result);
            }
          }
        }
      }

      logger.test("client / server with more conversation");
      {
        String msg1 = "hello foo!";
        String msg2 = "and how are you?";
        String msg3 = "oh, fine";

        try (Web.Server primaryServer = web.startServer(es)) {
          try (Web.SocketWrapper client = web.startClient(primaryServer)) {
            try (Web.SocketWrapper server = primaryServer.getServer(client)) {

              // client sends, server receives
              client.send(withNewline(msg1));
              assertEquals(msg1, server.readLine());

              // server sends, client receives
              server.send(withNewline(msg2));
              assertEquals(msg2, client.readLine());

              // client sends, server receives
              client.send(withNewline(msg3));
              assertEquals(msg3, server.readLine());
            }
          }
        }
      }

      logger.test("What happens if we throw an exception in a thread");
      {
        es.submit(() -> {throw new RuntimeException("No worries folks, just testing the exception handling");});
      }

      logger.test("like we're a web server");
      {
        try (Web.Server primaryServer = web.startServer(es)) {
          try (Web.SocketWrapper client = web.startClient(primaryServer)) {
            try (Web.SocketWrapper server = primaryServer.getServer(client)) {
              // send a GET request
              client.send("GET /index.html HTTP/1.1\r\n");
              client.send("cookie: abc=123\r\n");
              client.send("\r\n");
              server.send("HTTP/1.1 200 OK\r\n");
            }
          }
        }
      }



    } catch (Exception ex) {
      logger.logDebug(() -> Logger.printStackTrace(ex));
    }
    logger.stop();
    es.shutdownNow();
  }

  /**
    * A helper for testing - assert two integers are equal
    */
  private static void assertEquals(int left, int right) {
    if (left != right) {
      throw new RuntimeException("Not equal! left: " + left + " right: " + right);
    }
  }

  private static void assertEquals(String left, String right) {
    if (!left.equals(right)) {
      throw new RuntimeException("Not equal! left: " + left + " right: " + right);
    }
  }

  private static String withNewline(String msg) {
    return msg +"\n";
  }

}

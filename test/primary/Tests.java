package primary;

class Tests {

  public static void main(String[] args) {
    // testing a happy path
    {
      int result = Main.add(2,3);
      assertEquals(result, 5);
    }

    // testing a couple negatives
    {
      int result = Main.add(-2,-3);
      assertEquals(result, -5);
    }

    // testing with zeros
    {
      int result = Main.add(0,0);
      assertEquals(result, 0);
    }

    // test client / server
    {
      try (Web.Server primaryServer = Web.startServer()) {
        Web.SocketWrapper client = Web.startClient(primaryServer);
        Web.SocketWrapper server = primaryServer.getSocketWrapperByRemoteAddr(client.getLocalAddr(), client.getLocalPort());
        client.send("hello foo!\n");
        String result = server.readLine();
        assertEquals("hello foo!", result);
      }
    }

    // test client / server with more conversation
    {
      String msg1 = "hello foo!";
      String msg2 = "and how are you?";
      String msg3 = "oh, fine";

      try (Web.Server primaryServer = Web.startServer()) {
        Web.SocketWrapper client = Web.startClient(primaryServer);
        Web.SocketWrapper server = primaryServer.getSocketWrapperByRemoteAddr(client.getLocalAddr(), client.getLocalPort());

        System.out.println("client sends, server receives");
        client.send(withNewline(msg1));
        assertEquals(msg1, server.readLine());

        System.out.println("server sends, client receives");
        server.send(withNewline(msg2));
        assertEquals(msg2, client.readLine());

        System.out.println("client sends, server receives");
        client.send(withNewline(msg3));
        assertEquals(msg3, server.readLine());
      }
    }

    // test like we're a web server
    {
      try (Web.Server primaryServer = Web.startServer()) {
        Web.SocketWrapper client = Web.startClient(primaryServer);
        Web.SocketWrapper server = primaryServer.getSocketWrapperByRemoteAddr(client.getLocalAddr(), client.getLocalPort());

        // send a GET request
        client.send("GET /index.html HTTP/1.1\r\n");
        client.send("cookie: abc=123\r\n");
        client.send("\r\n");
        server.send("HTTP/1.1 200 OK\r\n");
      }
    }

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

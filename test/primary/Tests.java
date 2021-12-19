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
     // Client client = Comms.startClient();
     // Server server = Comms.startServerWithWait();
     // client.connectTo(server);
     // client.send("hello");
     // String result = server.receive();
     // assertEquals("hello", result);
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

}

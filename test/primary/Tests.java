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
      Web.Server server = Web.startServer();
      Web.Client client = Web.startClient(server);
      client.send("hello foo!\n");
      String result = server.readLine();
      assertEquals("hello foo!", result);
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

}

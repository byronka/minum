package primary;

import logging.TestLogger;
import utils.ExtendedExecutor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static primary.Web.StartLine.startLineRegex;

class Tests {

  public static void main(String[] args) {

    ExecutorService es = ExtendedExecutor.makeExecutorService();
    TestLogger logger = new TestLogger(es); //.turnOff(Logger.Type.DEBUG);

    try {

      Web web = new Web(logger);

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

      // no need to run this every time.  Feel free to uncomment this and make
      // sure it works, but seeing exceptions in the output from tests is disconcerting.
      logger.testSkip("What happens if we throw an exception in a thread");
      {
         // es.submit(() -> {throw new RuntimeException("No worries folks, just testing the exception handling");});
      }

      logger.test("like we're a web server");
      {
        try (Web.Server primaryServer = web.startServer(es)) {
          try (Web.SocketWrapper client = web.startClient(primaryServer)) {
            try (Web.SocketWrapper server = primaryServer.getServer(client)) {
              // send a GET request
              client.sendHttpLine("GET /index.html HTTP/1.1");
              client.sendHttpLine("cookie: abc=123");
              client.sendHttpLine("");
              server.sendHttpLine("HTTP/1.1 200 OK");
            }
          }
        }
      }

      /*
        If we provide some code to handle things on the server
        side when it accepts a connection, then it will more
        truly act like the web server we want it to be.
       */
      logger.test("starting server with a handler");
      {
        /*
          Simplistic proof-of-concept of the primary server
          handler.  The socket has been created and as new
          clients call it, this method handles each request.

          There's nothing to prevent us using this as the entire
          basis of a web framework.
         */
        Consumer<Web.SocketWrapper> handler = (sw) -> logger.logDebug(sw::readLine);

        try (Web.Server primaryServer = web.startServer(es, handler)) {
          try (Web.SocketWrapper client = web.startClient(primaryServer)) {
            // send a GET request
            client.sendHttpLine("GET /index.html HTTP/1.1");

            // give the server time to run code from the handler,
            // then shut down.
            try {
              primaryServer.centralLoopFuture.get(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
              e.printStackTrace();
            }
          }
        }
      }

      logger.test("starting server with a handler part 2");
      {

        try (Web.Server primaryServer = web.startServer(es, Web.HttpUtils.serverHandler)) {
          try (Web.SocketWrapper client = web.startClient(primaryServer)) {
            // send a GET request
            client.sendHttpLine("GET /add_two_numbers?a=42&b=44 HTTP/1.1");
            client.sendHttpLine("Host: localhost:8080");
            client.sendHttpLine("");

            Web.StatusLine statusLine = Web.StatusLine.extractStatusLine(client.readLine());

            assertEquals(statusLine.rawValue, "HTTP/1.1 200 OK");
                       
            Web.HeaderInformation hi = Web.HeaderInformation.extractHeaderInformation(client);

            List<String> expectedResponseHeaders = Arrays.asList(
                    "Server: atqa",
                    "Date: Sun, 16 Jan 2022 19:30:06 GMT",
                    "Content-Type: text/plain; charset=UTF-8",
                    "Content-Length: 2"
                    );

            assertEqualsDisregardOrder(hi.rawValues, expectedResponseHeaders);

            String body = Web.HttpUtils.readBody(client, hi.contentLength);

            assertEquals(body, "86");

            // give the server time to run code from the handler,
            // then shut down.
            try {
              primaryServer.centralLoopFuture.get(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
              e.printStackTrace();
            }
          }
        }
      }

      // Just a simple test while controlling the fake socket wrapper
      logger.test("TDD of a handler");
      {
        FakeSocketWrapper sw = new FakeSocketWrapper();
        AtomicReference<String> result = new AtomicReference<>();
        sw.sendHttpLineAction = s -> result.set(s);

        // this is what we're really going to test
        Consumer<Web.ISocketWrapper> handler = (socketWrapper) -> socketWrapper.sendHttpLine("this is a test");
        handler.accept(sw);

        assertEquals("this is a test", result.get());
      }

      // See https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages#start_line
      // 1. the method (GET, POST, etc.)
      // 2. the request target
      // 3. the HTTP version (e.g. HTTP/1.1)
      logger.test("We should be able to pull valuable information from the start line");
      {
        Matcher m = startLineRegex.matcher("GET /index.html HTTP/1.1");
        assertTrue(m.matches());
      }

      logger.test("playing around with how we could determine testedness of a function");
      {
        int score = 0;

        // for a pretend method, add(int a, int b)... let's play
        // e.g. add(2, 3)
        int a = 2;
        int b = 3;
        // make sure we hit less than zero, on zero, greater-than zero, both params
        if (a > 0) score++;if (a < 0) score++;if (a == 0) score++;
        if (b > 0) score++;if (b < 0) score++;if (b == 0) score++;
        // make sure we hit max and min
        if (a == Integer.MAX_VALUE) score++; if (a == Integer.MIN_VALUE) score++;
        if (b == Integer.MAX_VALUE) score++; if (b == Integer.MIN_VALUE) score++;
        // now we've dealt with each individually, let's think how they act as pairs
        if (a < 0 && b < 0) score++; if (a > 0 && b > 0) score++; if (a == 0 && b == 0) score++;
        if (a < 0 && b > 0) score++; if (a > 0 && b < 0) score++; if (a == 0 && b != 0) score++;
        if (a != 0 && b == 0) score++;
        if (a == Integer.MAX_VALUE && b == Integer.MAX_VALUE) score++;
        if (a == Integer.MIN_VALUE && b == Integer.MIN_VALUE) score++;

        int finalScore = score;
        logger.logDebug(() -> "Looks like your testedness score is " + finalScore);
      }

      /*
        If we can basically just try casting to things and making comparisons, then we might
        get a leg up for those situations where we deal with non-typed params
       */
      logger.test("how do we test non-typed code? a single param that turns out to be an int");
      {
        int score = 0;

        // pretend method: foo(Object a)
        Object a = 42;

        // make sure we hit less than zero, on zero, greater-than zero, both params
        try {
          int stuff = (int) a;
          if (stuff > 0) score++;
          if (stuff < 0) score++;
          if (stuff == 0) score++;
        } catch (ClassCastException ex) {
          logger.logDebug(() -> "this is not an int");
        }
        try {
          long stuff = (long) a;
          if (stuff > 0) score++;
          if (stuff < 0) score++;
          if (stuff == 0) score++;
        } catch (ClassCastException ex) {
          logger.logDebug(() -> "this is not a long");
        }

        int finalScore = score;
        logger.logDebug(() -> "Looks like your testedness score is " + finalScore);

      }

      logger.test("some more exotic type tests");
      {
        int score = 0;

        // pretend method: foo(String[] a, Foobar b)
        String[] a = {"a", "b", "c"};
        class Foobar {
          public Foobar() {}
          public int bar() {return 42;}
        }
        Foobar f = new Foobar();

        if (a.length == 0) score++;
        if (a.length > 0) score++;
        if (a == null) score++;
        if (a.length == 1) score++;

        if (f == null) score++;

        int finalScore = score;
        logger.logDebug(() -> "Looks like your testedness score is " + finalScore);
      }

      // TODO: test extractStartLine - GET /something HTTP/1.1
      // TODO: test extractStartLine - POST /something HTTP/1.0
      // TODO: test failure extractStartLine -  /something HTTP/1.1
      // TODO: test failure extractStartLine - GET HTTP/1.1
      // TODO: test failure extractStartLine - GET /something
      // TODO: test failure extractStartLine - GET /something HTTP/1.2
      // TODO: test failure extractStartLine - GET /something HTTP/

      // TODO test extractStatusLine - HTTP/1.1 200 OK
      // TODO test failure extractStatusLine - HTTP/1.1 200
      // TODO test failure extractStatusLine - HTTP/1.1  OK
      // TODO test failure extractStatusLine - HTTP 200 OK
      // TODO test failure extractStatusLine - HTTP/1.3 200 OK
      // TODO test failure extractStatusLine - HTTP/1.1 199 OK

    } finally {
      // final shutdown pieces
      logger.stop();
      es.shutdownNow();
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

  /**
   * asserts two lists are equal, ignoring the order.
   * For example, (a, b) is equal to (b, a)
   */
  private static <T> void assertEqualsDisregardOrder(List<T> left, List<T> right) {
    if (left.size() != right.size()) {
      throw new RuntimeException(String.format("different sizes: left was %d, right was %d%n", left.size(), right.size()));
    }
    List<T> orderedLeft = left.stream().sorted().toList();
    List<T> orderedRight = right.stream().sorted().toList();

    for (int i = 0; i < left.size(); i++) {
      if (!orderedLeft.get(i).equals(orderedRight.get(i))) {
        throw new RuntimeException(String.format("different values: left: %s right: %s", orderedLeft.get(i), orderedRight.get(i)));
      }
    }
  }

  /**
   * asserts that two lists are equal in value and order.
   * For example, (a, b) is equal to (a, b)
   * Does not expect null as an input value.
   * Two empty lists are considered equal.
   */
  private static <T> void assertEquals(List<T> left, List<T> right) {
    if (left.size() != right.size()) {
      throw new RuntimeException(String.format("different sizes: left was %d, right was %d%n", left.size(), right.size()));
    }
    for (int i = 0; i < left.size(); i++) {
      if (!left.get(i).equals(right.get(i))) {
        throw new RuntimeException(String.format("different values: left: %s right: %s", left.get(i), right.get(i)));
      }
    }
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new RuntimeException("value was unexpectedly false");
    }
  }

  private static String withNewline(String msg) {
    return msg +"\n";
  }

}

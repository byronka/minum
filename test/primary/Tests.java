package primary;

import logging.Logger;
import utils.ExtendedExecutor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Tests {

  public static void main(String[] args) {

    ExecutorService es = ExtendedExecutor.makeExecutorService();
    Logger logger = new Logger(es).initialize(); //.turnOff(Logger.Type.DEBUG);

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
      // logger.test("What happens if we throw an exception in a thread");
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

      /**
       * If we provide some code to handle things on the server
       * side when it accepts a connection, then it will more
       * truly act like the web server we want it to be.
       */
      logger.test("starting server with a handler");
      {
        /**
         * Simplistic proof-of-concept of the primary server
         * handler.  The socket has been created and as new
         * clients call it, this method handles each request.
         *
         * There's nothing to prevent us using this as the entire
         * basis of a web framework.
         */
        Consumer<Web.SocketWrapper> handler = (sw) -> logger.logDebug(sw::readLine);

        try (Web.Server primaryServer = web.startServer(es, handler)) {
          try (Web.SocketWrapper client = web.startClient(primaryServer)) {
            // send a GET request
            client.sendHttpLine("GET /index.html HTTP/1.1");

            // give the server time to run code from the handler,
            // then shut down.
            primaryServer.centralLoopFuture.get(10, TimeUnit.MILLISECONDS);
          } catch (Exception ex) {
            // do nothing
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
        Pattern startLineRegex = Pattern.compile("(GET|POST) /(.*) HTTP/(?:1.1|1.0)");
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

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new RuntimeException("value was unexpectedly false");
    }
  }

  private static String withNewline(String msg) {
    return msg +"\n";
  }

}

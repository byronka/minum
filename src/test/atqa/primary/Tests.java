package atqa.primary;

import atqa.FullSystem;
import atqa.database.SimpleDatabaseTests;
import atqa.logging.TestLogger;
import atqa.utils.ExtendedExecutor;
import atqa.utils.InvariantException;
import atqa.utils.ThrowingConsumer;
import atqa.web.*;

import java.io.IOException;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;

import static atqa.framework.TestFramework.*;
import static atqa.web.StartLine.startLineRegex;
import static atqa.web.StatusLine.StatusCode._200_OK;

public class Tests {

  static final ZonedDateTime default_zdt = ZonedDateTime.of(2022, Month.JANUARY.getValue(), 4, 9, 25, 0, 0, ZoneId.of("UTC"));


  /*$      /$$           /$$               /$$                           /$$
| $$  /$ | $$          | $$              | $$                          | $$
| $$ /$$$| $$  /$$$$$$ | $$$$$$$        /$$$$$$    /$$$$$$   /$$$$$$$ /$$$$$$   /$$$$$$$
| $$/$$ $$ $$ /$$__  $$| $$__  $$      |_  $$_/   /$$__  $$ /$$_____/|_  $$_/  /$$_____/
| $$$$_  $$$$| $$$$$$$$| $$  \ $$        | $$    | $$$$$$$$|  $$$$$$   | $$   |  $$$$$$
| $$$/ \  $$$| $$_____/| $$  | $$        | $$ /$$| $$_____/ \____  $$  | $$ /$$\____  $$
| $$/   \  $$|  $$$$$$$| $$$$$$$/        |  $$$$/|  $$$$$$$ /$$$$$$$/  |  $$$$//$$$$$$$/
|__/     \__/ \_______/|_______/          \___/   \_______/|_______/    \___/ |______*/
  public static void webTests(ExecutorService es, TestLogger logger) throws IOException {

    Web web = new Web(logger);

    logger.test("client / server");{
      try (Server primaryServer = web.startServer(es)) {
        try (SocketWrapper client = web.startClient(primaryServer)) {
          try (SocketWrapper server = primaryServer.getServer(client)) {

            client.send("hello foo!\n");
            String result = server.readLine();
            assertEquals("hello foo!", result);
          }
        }
      }
    }

    logger.test("client / server with more conversation");{
      String msg1 = "hello foo!";
      String msg2 = "and how are you?";
      String msg3 = "oh, fine";

      try (Server primaryServer = web.startServer(es)) {
        try (SocketWrapper client = web.startClient(primaryServer)) {
          try (SocketWrapper server = primaryServer.getServer(client)) {

            // client sends, server receives
            client.sendHttpLine(msg1);
            assertEquals(msg1, server.readLine());

            // server sends, client receives
            server.sendHttpLine(msg2);
            assertEquals(msg2, client.readLine());

            // client sends, server receives
            client.sendHttpLine(msg3);
            assertEquals(msg3, server.readLine());
          }
        }
      }
    }

    // no need to run this every time.  Feel free to uncomment this and make
    // sure it works, but seeing exceptions in the output from tests is disconcerting.
    logger.testSkip("What happens if we throw an exception in a thread");{
      // es.submit(() -> {throw new RuntimeException("No worries folks, just testing the exception handling");});
    }

    logger.test("like we're a atqa.web server");{
      try (Server primaryServer = web.startServer(es)) {
        try (SocketWrapper client = web.startClient(primaryServer)) {
          try (SocketWrapper server = primaryServer.getServer(client)) {
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
      truly act like the atqa.web server we want it to be.
     */
    logger.test("starting server with a handler");{
      /*
        Simplistic proof-of-concept of the atqa.primary server
        handler.  The socket has been created and as new
        clients call it, this method handles each request.

        There's nothing to prevent us using this as the entire
        basis of a atqa.web atqa.framework.
       */
      ThrowingConsumer<SocketWrapper, IOException> handler = (sw) -> logger.logDebug(sw::readLine);

      try (Server primaryServer = web.startServer(es, handler)) {
        try (SocketWrapper client = web.startClient(primaryServer)) {
          // send a GET request
          client.sendHttpLine("GET /index.html HTTP/1.1");

          // give the server time to run code from the handler,
          // then shut down.
          try {
            primaryServer.centralLoopFuture.get(10, TimeUnit.MILLISECONDS);
          } catch (Exception e) {
            // do nothing
          }
        }
      }
    }

    /*
     * This class belongs to the test below, "starting server with a handler part 2"
     *
     * This represents the shape an endpoint could take. It's given a request object,
     * we presume it has everything is needs to do its work (query strings on the path,
     * header values, body contents, all that stuff), and it replies with a response
     * object that gets ferried along to the client.
     */
    class Summation {
      static Response addTwoNumbers(Request r) {
        int aValue = Integer.parseInt(r.startLine().queryString().get("a"));
        int bValue = Integer.parseInt(r.startLine().pathDetails().queryString().get("b"));
        int sum = aValue + bValue;
        String sumString = String.valueOf(sum);
        return new Response(_200_OK, ContentType.TEXT_HTML, sumString);
      }
    }

    logger.test("starting server with a handler part 2");{
      Frame wf = new Frame(logger, default_zdt);
      wf.registerPath(StartLine.Verb.GET, "add_two_numbers", Summation::addTwoNumbers);
      try (Server primaryServer = web.startServer(es, wf.makeHandler())) {
        try (SocketWrapper client = web.startClient(primaryServer)) {
          // send a GET request
          client.sendHttpLine("GET /add_two_numbers?a=42&b=44 HTTP/1.1");
          client.sendHttpLine("Host: localhost:8080");
          client.sendHttpLine("");

          StatusLine statusLine = StatusLine.extractStatusLine(client.readLine());

          assertEquals(statusLine.rawValue(), "HTTP/1.1 200 OK");

          Headers hi = Headers.extractHeaderInformation(client);

          List<String> expectedResponseHeaders = Arrays.asList(
                  "Server: atqa",
                  "Date: Tue, 4 Jan 2022 09:25:00 GMT",
                  "Content-Type: text/html; charset=UTF-8",
                  "Content-Length: 2"
          );

          assertEqualsDisregardOrder(hi.rawValues(), expectedResponseHeaders);

          String body = HttpUtils.readBody(client, hi.contentLength());

          assertEquals(body, "86");

          // give the server time to run code from the handler,
          // then shut down.
          try {
            primaryServer.centralLoopFuture.get(10, TimeUnit.MILLISECONDS);
          } catch (Exception e) {
            // do nothing
          }
        }
      }
    }

    // Just a simple test while controlling the fake socket wrapper
    logger.test("TDD of a handler");{
      FakeSocketWrapper sw = new FakeSocketWrapper();
      AtomicReference<String> result = new AtomicReference<>();
      sw.sendHttpLineAction = s -> result.set(s);

      // this is what we're really going to test
      ThrowingConsumer<ISocketWrapper, IOException> handler = (socketWrapper) -> socketWrapper.sendHttpLine("this is a test");
      handler.accept(sw);

      assertEquals("this is a test", result.get());
    }

    // See https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages#start_line
    // 1. the method (GET, POST, etc.)
    // 2. the request target
    // 3. the HTTP version (e.g. HTTP/1.1)
    logger.test("We should be able to pull valuable information from the start line");{
      Matcher m = startLineRegex.matcher("GET /index.html HTTP/1.1");
      assertTrue(m.matches());
    }

    logger.test("alternate case for extractStartLine - POST");{
      StartLine sl = StartLine.extractStartLine("POST /something HTTP/1.0");
      assertEquals(sl.verb(), StartLine.Verb.POST);
    }

    logger.test("alernate case - empty path");{
      StartLine sl = StartLine.extractStartLine("GET / HTTP/1.1");
      assertEquals(sl.verb(), StartLine.Verb.GET);
      assertEquals(sl.pathDetails().isolatedPath(), "");
    }

    logger.test("negative cases for extractStartLine");{
      // missing verb
      final var ex1 = assertThrows(InvariantException.class, "/something HTTP/1.1 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$", () -> StartLine.extractStartLine("/something HTTP/1.1"));
      assertEquals(ex1.getMessage(), "/something HTTP/1.1 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$");

      // missing path
      final var ex2 = assertThrows(InvariantException.class, "GET HTTP/1.1 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$", () -> StartLine.extractStartLine("GET HTTP/1.1"));
      assertEquals(ex2.getMessage(), "GET HTTP/1.1 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$");

      // missing HTTP version
      final var ex3 = assertThrows(InvariantException.class, "GET /something must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$", () -> StartLine.extractStartLine("GET /something"));
      assertEquals(ex3.getMessage(), "GET /something must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$");

      // invalid HTTP version
      final var ex4 = assertThrows(InvariantException.class, "GET /something HTTP/1.2 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$", () -> StartLine.extractStartLine("GET /something HTTP/1.2"));
      assertEquals(ex4.getMessage(), "GET /something HTTP/1.2 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$");

      // invalid HTTP version syntax
      final var ex5 = assertThrows(InvariantException.class, "GET /something HTTP/ must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$", () -> StartLine.extractStartLine("GET /something HTTP/"));
      assertEquals(ex5.getMessage(), "GET /something HTTP/ must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$");
    }

    logger.test("positive test for extractStatusLine");{
      StatusLine sl = StatusLine.extractStatusLine("HTTP/1.1 200 OK");
      assertEquals(sl.status(), _200_OK);
    }

    logger.test("negative tests for extractStatusLine");{
      // missing status description
      assertThrows(InvariantException.class, "HTTP/1.1 200 must match the statusLinePattern: ^HTTP/(1.1|1.0) (\\d{3}) (.*)$", () -> StatusLine.extractStatusLine("HTTP/1.1 200"));
      // missing status code
      assertThrows(InvariantException.class, "HTTP/1.1  OK must match the statusLinePattern: ^HTTP/(1.1|1.0) (\\d{3}) (.*)$", () -> StatusLine.extractStatusLine("HTTP/1.1  OK"));
      // missing HTTP version
      assertThrows(InvariantException.class, "HTTP 200 OK must match the statusLinePattern: ^HTTP/(1.1|1.0) (\\d{3}) (.*)$", () -> StatusLine.extractStatusLine("HTTP 200 OK"));
      // invalid HTTP version
      assertThrows(InvariantException.class, "HTTP/1.3 200 OK must match the statusLinePattern: ^HTTP/(1.1|1.0) (\\d{3}) (.*)$", () -> StatusLine.extractStatusLine("HTTP/1.3 200 OK"));
      // invalid status code
      assertThrows(NoSuchElementException.class, "No value present", () -> StatusLine.extractStatusLine("HTTP/1.1 199 OK"));
    }

    /*
    as part of sending data to the server, we'll encode data like the following.  if we
    set value_a to 123 and value_b to 456, it looks like: value_a=123&value_b=456

    we want to convert that string to a map, like this: value_a -> 123, value_b -> 456
     */
    logger.test("parseUrlEncodedForm should properly parse data");{
      final var expected = Map.of("value_a", "123", "value_b", "456");
      final var result = Frame.parseUrlEncodedForm("value_a=123&value_b=456");
      assertEquals(expected, result);
    }

    logger.test("parseUrlEncodedForm edge cases"); {
      // splitting on equals
      final var ex1 = assertThrows(InvariantException.class, () -> Frame.parseUrlEncodedForm("value_a=123=456"));
      assertEquals(ex1.getMessage(), "Splitting on = should return 2 values.  Input was value_a=123=456");

      // blank key
      final var ex2 = assertThrows(InvariantException.class, () -> Frame.parseUrlEncodedForm("=123"));
      assertEquals(ex2.getMessage(), "The key must not be blank");

      // duplicate keys
      final var ex3 = assertThrows(InvariantException.class, () -> Frame.parseUrlEncodedForm("a=123&a=123"));
      assertEquals(ex3.getMessage(), "a was duplicated in the post body - had values of 123 and 123");
    }

    logger.test("when we post data to an endpoint, it can extract the data"); {
      Frame wf = new Frame(logger, default_zdt);
      wf.registerPath(StartLine.Verb.POST, "some_post_endpoint", (x) -> new Response(_200_OK, ContentType.TEXT_HTML, x.body()));
      try (Server primaryServer = web.startServer(es, wf.makeHandler())) {
        try (SocketWrapper client = web.startClient(primaryServer)) {

          final var postedData = "value_a=123&value_b=456";

          // send a POST request
          client.sendHttpLine("POST /some_post_endpoint HTTP/1.1");
          client.sendHttpLine("Host: localhost:8080");
          client.sendHttpLine("Content-Length: " + postedData.length());
          client.sendHttpLine("");
          client.sendHttpLine(postedData);

          StatusLine.extractStatusLine(client.readLine());
          Headers hi = Headers.extractHeaderInformation(client);
          String body = HttpUtils.readBody(client, hi.contentLength());

          assertEquals(body, "value_a=123&value_b=456");

          // give the server time to run code from the handler,
          // then shut down.
          try {
            primaryServer.centralLoopFuture.get(10, TimeUnit.MILLISECONDS);
          } catch (Exception e) {
            // do nothing
          }
        }
      }
    }

    logger.test("when the requested endpoint does not exist, we get a 404 response"); {
      Frame wf = new Frame(logger, default_zdt);
      try (Server primaryServer = web.startServer(es, wf.makeHandler())) {
        try (SocketWrapper client = web.startClient(primaryServer)) {

          // send a GET request
          client.sendHttpLine("GET /some_endpoint HTTP/1.1");
          client.sendHttpLine("Host: localhost:8080");
          client.sendHttpLine("");

          StatusLine statusLine = StatusLine.extractStatusLine(client.readLine());
          assertEquals(statusLine.rawValue(), "HTTP/1.1 404 NOT FOUND");

          // give the server time to run code from the handler,
          // then shut down.
          try {
            primaryServer.centralLoopFuture.get(10, TimeUnit.MILLISECONDS);
          } catch (Exception e) {
            // do nothing
          }
        }
      }
    }

    logger.test("when the client stops talking to the server, the endpoint handler bails"); {
      Frame wf = new Frame(logger, default_zdt);
      try (Server primaryServer = web.startServer(es, wf.makeHandler())) {
        try (SocketWrapper client = web.startClient(primaryServer)) {

          // send a GET request
          client.close();

          // give the server time to run code from the handler,
          // then shut down.
          try {
            primaryServer.centralLoopFuture.get(10, TimeUnit.MILLISECONDS);
          } catch (Exception e) {
            // do nothing
          }
        }
      }
    }

    /*
     * The StaticFilesCache is memory storage for all the files we
     * are sending to clients that don't (typically) change during
     * the run of the server.  Things like style pages, banner images,
     * non-dynamic scripts.
     *
     * This test presumes that there are static files in the
     * main/static directory.
     */
    logger.test("StaticFilesCache should load static files into a map"); {
      final var sfc = new StaticFilesCache(logger).loadStaticFiles();
      assertTrue(sfc.getSize() > 0);
    }

  }

  /*$$$$$$$                    /$$                                         /$$                     /$$
 |__  $$__/                   | $$                                        | $$                    |__/
    | $$  /$$$$$$   /$$$$$$$ /$$$$$$          /$$$$$$  /$$$$$$$   /$$$$$$ | $$ /$$   /$$  /$$$$$$$ /$$  /$$$$$$$
    | $$ /$$__  $$ /$$_____/|_  $$_/         |____  $$| $$__  $$ |____  $$| $$| $$  | $$ /$$_____/| $$ /$$_____/
    | $$| $$$$$$$$|  $$$$$$   | $$            /$$$$$$$| $$  \ $$  /$$$$$$$| $$| $$  | $$|  $$$$$$ | $$|  $$$$$$
    | $$| $$_____/ \____  $$  | $$ /$$       /$$__  $$| $$  | $$ /$$__  $$| $$| $$  | $$ \____  $$| $$ \____  $$
    | $$|  $$$$$$$ /$$$$$$$/  |  $$$$/      |  $$$$$$$| $$  | $$|  $$$$$$$| $$|  $$$$$$$ /$$$$$$$/| $$ /$$$$$$$/
    |__/ \_______/|_______/    \___/         \_______/|__/  |__/ \_______/|__/ \____  $$|_______/ |__/|_______/
                                                                            /$$  | $$
                                                                           |  $$$$$$/
                                                                            \_____*/
  public static void testAnalysisTests(TestLogger logger) {


    // region Test Analysis section

    logger.test("playing around with how we could determine testedness of a function");{
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
    logger.test("how do we test non-typed code? a single param that turns out to be an int");{
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

    logger.test("some more exotic type tests");{
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

  }

  public static void main(String[] args) throws Exception {
    testFullSystem_Soup_To_Nuts();

    final var es = ExtendedExecutor.makeExecutorService();
    final var logger = new TestLogger(es);

    webTests(es, logger);
    testAnalysisTests(logger);
    new SimpleDatabaseTests(logger).tests(es);

    // shut the test threads down
    logger.stop();
    es.shutdownNow();
  }

  private static void testFullSystem_Soup_To_Nuts() throws IOException {
    final var es = ExtendedExecutor.makeExecutorService();
    final var logger = new TestLogger(es); //.turnOff(Logger.Type.DEBUG);
    var fs = new FullSystem(logger, es).start();
    fs.shutdown();
    es.shutdownNow();
  }

}

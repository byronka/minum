package atqa.primary;

import atqa.FullSystem;
import atqa.database.SimpleDatabaseTests;
import atqa.instrumentation.InstrumentationTests;
import atqa.logging.TestLogger;
import atqa.utils.ExtendedExecutor;
import atqa.utils.MyThread;
import atqa.utils.StringUtilsTests;
import atqa.web.WebTests;
import atqa.web.WebTests2;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class Tests {

  public static void main(String[] args) throws Exception {
    testFullSystem_Soup_To_Nuts();
    unitAndIntegrationTests();
  }

  /**
   * These tests range in size from focusing on very small elements (unit tests)
   * to larger combinations of methods and classes (integration tests) but
   * stop short of running {@link FullSystem}
   */
  private static void unitAndIntegrationTests() {
    try (final var es = ExtendedExecutor.makeExecutorService()) {
      try {
        final var logger = new TestLogger(es);

        // Arrr here be the tests
        new WebTests(logger).tests(es);
        new TestAnalysisTests(logger).tests();
        new SimpleDatabaseTests(logger).tests(es);
        new InstrumentationTests(logger).tests(es);
        new WebTests2(logger).tests(es);
        new StringUtilsTests(logger).tests();

      } catch (Exception ex) {
        ex.printStackTrace();
      }
      runShutdownSequence(es);
    }
  }

  private static void runShutdownSequence(ExecutorService es) {
    MyThread.sleep(50);
    System.out.printf("%n%n");
    System.out.println("-------------------------");
    System.out.println("----  Tests finished ---- ");
    System.out.println("----  Shutting down  ---- ");
    System.out.println("-------------------------");
    System.out.printf("%n%n");
    es.shutdownNow();
  }

  private static void testFullSystem_Soup_To_Nuts() throws IOException {
    try (final var es = ExtendedExecutor.makeExecutorService()) {
      final var logger = new TestLogger(es); //.turnOff(Logger.Type.DEBUG);
      var fs = new FullSystem(logger, es).start();
      fs.shutdown();
      es.shutdownNow();
    }

    // need to wait for port 8080 to be closed by the TCP system
    MyThread.sleep(200);
  }

}

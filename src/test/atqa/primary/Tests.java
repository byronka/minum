package atqa.primary;

import atqa.FullSystem;
import atqa.logging.TestLogger;
import atqa.utils.ExtendedExecutor;
import atqa.utils.MyThread;
import atqa.web.WebTests2;

import java.io.IOException;

public class Tests {

  public static void main(String[] args) throws Exception {
//     testFullSystem_Soup_To_Nuts();
    unitAndIntegrationTests();
  }

  /**
   * These tests range in size from focusing on very small elements (unit tests)
   * to larger combinations of methods and classes (integration tests) but
   * stop short of running {@link FullSystem}
   */
  private static void unitAndIntegrationTests() throws IOException, ClassNotFoundException {
    try (final var es = ExtendedExecutor.makeExecutorService()) {
      try {
        final var logger = new TestLogger(es);

//        new WebTests(logger).tests(es);
        new WebTests2(logger).tests(es);
//        new TestAnalysisTests(logger).tests();
//        new SimpleDatabaseTests(logger).tests(es);
//        new InstrumentationTests(logger).tests(es);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      es.shutdownNow();
    }
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

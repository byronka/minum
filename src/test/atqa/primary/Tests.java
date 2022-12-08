package atqa.primary;

import atqa.FullSystem;
import atqa.database.SimpleDatabaseTests;
import atqa.instrumentation.InstrumentationTests;
import atqa.utils.ExtendedExecutor;
import atqa.utils.MyThread;
import atqa.utils.StringUtilsTests;
import atqa.web.WebTests;
import atqa.auth.AuthenticationTests;

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
   * stop short of running {@link FullSystem}.  For that purpose, see {@link #testFullSystem_Soup_To_Nuts()}
   */
  private static void unitAndIntegrationTests() {
    try (final var es = ExtendedExecutor.makeExecutorService()) {
      try {
        new WebTests(es).tests();
        new TestAnalysisTests(es).tests();
        new SimpleDatabaseTests(es).tests();
        new InstrumentationTests(es).tests();
        new StringUtilsTests(es).tests();
        new AuthenticationTests(es).tests();

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

  /**
   * Run a test of the entire system.  In particular, runs code
   * from {@link FullSystem}
   */
  private static void testFullSystem_Soup_To_Nuts() throws IOException {
    try (final var es = ExtendedExecutor.makeExecutorService()) {
      var fs = new FullSystem(es).start();
      fs.shutdown();
      es.shutdownNow();
    }

    // need to wait for port 8080 to be closed by the TCP system
    MyThread.sleep(200);
  }

}

package atqa.primary;

import atqa.FullSystem;
import atqa.database.SimpleDatabaseTests;
import atqa.instrumentation.InstrumentationTests;
import atqa.logging.TestLogger;
import atqa.photo.PhotoTests;
import atqa.sampledomain.SampleDomainTests;
import atqa.utils.ExtendedExecutor;
import atqa.utils.FileUtils;
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
    final var es = ExtendedExecutor.makeExecutorService();
    final var logger = new TestLogger(es);
    try {
      // Arrr here be the tests
      new WebTests(logger).tests(es);
      new TestAnalysisTests(logger).tests();
      new SimpleDatabaseTests(logger).tests(es);
      new InstrumentationTests(logger).tests(es);
      new StringUtilsTests(logger).tests();
      new AuthenticationTests(logger).tests(es);
      new SampleDomainTests(logger).tests(es);
      new PhotoTests(logger).tests(es);

    } catch (Exception ex) {
      logger.testPrint(TestLogger.printStackTrace(ex));
    }
    runShutdownSequence(es);
    clearTestDatabase(logger);
  }

  private static void clearTestDatabase(TestLogger logger) {
    try {
      FileUtils.deleteDirectoryRecursivelyIfExists("out/simple_db", logger);
    } catch (IOException e) {
      throw new RuntimeException(e);
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
    final var es = ExtendedExecutor.makeExecutorService();
    final var logger = new TestLogger(es);
    var fs = new FullSystem(logger, es).start();
    fs.shutdown();
    es.shutdownNow();

    // need to wait for port 8080 to be closed by the TCP system
    MyThread.sleep(200);
  }

}

package atqa.primary;

import atqa.FullSystem;
import atqa.auth.AuthenticationTests;
import atqa.database.SimpleDatabaseTests;
import atqa.instrumentation.InstrumentationTests;
import atqa.logging.TestLogger;
import atqa.photo.PhotoTests;
import atqa.sampledomain.SampleDomainTests;
import atqa.utils.FileUtils;
import atqa.utils.MyThread;
import atqa.utils.StringUtilsTests;
import atqa.web.WebTests;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class Tests {

  public static void main(String[] args) throws Exception {
    unitAndIntegrationTests();
    testFullSystem_Soup_To_Nuts();
    clearTestDatabase();
  }

  /**
   * These tests range in size from focusing on very small elements (unit tests)
   * to larger combinations of methods and classes (integration tests) but
   * stop short of running {@link FullSystem}.  For that purpose, see {@link #testFullSystem_Soup_To_Nuts()}
   */
  private static void unitAndIntegrationTests() {
    TestLogger logger = TestLogger.makeTestLogger();
    var es = logger.getExecutorService();
    try {
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
  }

  private static void clearTestDatabase() throws IOException {
      TestLogger logger = TestLogger.makeTestLogger();
      FileUtils.deleteDirectoryRecursivelyIfExists("out/simple_db", logger);
      runShutdownSequence(logger.getExecutorService());
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
    TestLogger logger = TestLogger.makeTestLogger();
    var es = logger.getExecutorService();
    var fs = new FullSystem(logger, es).start();
    fs.shutdown();
    es.shutdownNow();

    // need to wait for port 8080 to be closed by the TCP system
    MyThread.sleep(200);
  }

}

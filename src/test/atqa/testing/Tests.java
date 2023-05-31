package atqa.testing;

import atqa.FullSystem;
import atqa.auth.AuthenticationTests;
import atqa.database.SimpleDatabaseTests;
import atqa.sampledomain.FunctionalTests;
import atqa.sampledomain.ListPhotosTests;
import atqa.logging.TestLogger;
import atqa.primary.TestAnalysisTests;
import atqa.sampledomain.SampleDomainTests;
import atqa.utils.*;
import atqa.web.Http2Tests;
import atqa.web.WebTests;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

public class Tests {

  public static void main(String[] args) {
    try {
      unitAndIntegrationTests();
      clearTestDatabase();
      testFullSystem_Soup_To_Nuts();
      clearTestDatabase();
      indicateTestsFinished();
    } catch (Exception ex) {
      MyThread.sleep(100);
      ex.printStackTrace();
    }
  }

  private static void indicateTestsFinished() {
    System.out.println();
    System.out.println("-------------------------");
    System.out.println("----  Tests finished ---- ");
    System.out.println("-------------------------");
  }

  /**
   * These tests range in size from focusing on very small elements (unit tests)
   * to larger combinations of methods and classes (integration tests) but
   * stop short of running {@link FullSystem}.  For that purpose, see {@link #testFullSystem_Soup_To_Nuts()}
   */
  private static void unitAndIntegrationTests() throws Exception {
    TestLogger logger = TestLogger.makeTestLogger();
    var es = logger.getExecutorService();
    new WebTests(logger).tests(es);
    new SimpleDatabaseTests(logger).tests(es);
    new StringUtilsTests(logger).tests();
    new AuthenticationTests(logger).tests(es);
    new ListPhotosTests(logger).tests(es);
    new Http2Tests(logger).test(es);
    new SampleDomainTests(logger).tests(es);
    new TestAnalysisTests(logger).tests();
    runShutdownSequence(es);
  }

  private static void clearTestDatabase() throws IOException {
      TestLogger logger = TestLogger.makeTestLogger();
      FileUtils.deleteDirectoryRecursivelyIfExists(Path.of("out/simple_db"), logger);
      runShutdownSequence(logger.getExecutorService());
  }

  private static void runShutdownSequence(ExecutorService es) {
    ActionQueue.killAllQueues();
    es.shutdown();
  }

  /**
   * Run a test of the entire system.  In particular, runs code
   * from {@link FullSystem}
   */
  private static void testFullSystem_Soup_To_Nuts() throws Exception {
    TestLogger logger = TestLogger.makeTestLogger();
    logger.test("Starting a soup-to-nuts tests of the full system");
    var es = logger.getExecutorService();
    var fs = new FullSystem(logger, es).start();
    new FunctionalTests(logger, fs.server).test();
    fs.removeShutdownHook();
    fs.shutdown();
    es.shutdownNow();
  }

}

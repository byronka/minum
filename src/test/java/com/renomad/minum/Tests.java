package com.renomad.minum;

import com.renomad.minum.database.SimpleDatabaseTests;
import com.renomad.minum.sampledomain.LruCacheTests;
import com.renomad.minum.security.TheBrigTests;
import com.renomad.minum.testing.RegexUtilsTests;
import com.renomad.minum.testing.TestFrameworkTests;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.utils.*;
import com.renomad.minum.web.*;

import java.io.IOException;
import java.nio.file.Path;

import static com.renomad.minum.testing.TestFramework.buildTestingContext;

public class Tests {

  public static void main(String[] args) {
    var tests = new Tests();
    tests.start();
  }

  private void start() {
    try {
      unitAndIntegrationTests();
      testFullSystem_Soup_To_Nuts();
      indicateTestsFinished();
    } catch (Exception ex) {
      String exceptionString = StacktraceUtils.stackTraceToString(ex);
      System.out.println(exceptionString);
    }
  }

  public Tests() {
  }

  private void indicateTestsFinished() {
    MyThread.sleep(20);
    System.out.println();
    System.out.println("-------------------------");
    System.out.println("----  Tests finished ----");
    System.out.println("-------------------------");
    System.out.println();
    System.out.println("See test reports at out/reports/tests/\n");
  }

  /**
   * These tests range in size from focusing on very small elements (unit tests)
   * to larger combinations of methods and classes (integration tests) but
   * stop short of running {@link FullSystem}.
   */
  private void unitAndIntegrationTests() throws Exception {
    Context context = buildTestingContext("_unit_test");

    new TemplatingTests(context).tests();
    new FileUtilsTests(context).tests();
    new TheBrigTests(context).tests();
    new HtmlParserTests(context).tests();
    new TestFrameworkTests(context).tests();
    new ServerTests(context).tests();
    new StackTraceUtilsTests(context).tests();
    new BodyProcessorTests(context).tests();
    new ActionQueueTests(context).tests();
    new ThrowingRunnableTests(context).tests();
    new RegexUtilsTests(context).tests();
    new SearchUtilsTests(context).tests();

    handleShutdown(context);
  }

  /**
   * Run a test of the entire system.  In particular, runs code
   * from {@link FullSystem}
   */
  private void testFullSystem_Soup_To_Nuts() throws Exception {
    Context context = buildContextFunctionalTests();
    new FunctionalTests(context).test();
    shutdownFunctionalTests(context);
  }

  private void handleShutdown(Context context) throws IOException {
    var logger = (TestLogger) context.getLogger();
    logger.writeTestReport("unit_tests");
    context.getFileUtils().deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().DB_DIRECTORY), logger);
    new ActionQueueKiller(context).killAllQueues();
    context.getExecutorService().shutdownNow();
    context.getLogger().stop();
  }

  private Context buildContextFunctionalTests() throws IOException {
    System.out.println("Starting a soup-to-nuts tests of the full system");
    var context = buildTestingContext("_integration_test");
    new FullSystem(context).start();
    new TheRegister(context).registerDomains();
    return context;
  }

  private void shutdownFunctionalTests(Context context) throws IOException {
    // delay a sec so our system has time to finish before we start deleting files
    MyThread.sleep(300);
    context.getFileUtils().deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().DB_DIRECTORY), context.getLogger());
    var fs = context.getFullSystem();
    fs.close();
    ((TestLogger)context.getLogger()).writeTestReport("functional_tests");
    context.getLogger().stop();
    context.getExecutorService().shutdownNow();
  }

}

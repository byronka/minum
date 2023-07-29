package minum;

import minum.database.SimpleDatabaseTests;
import minum.logging.LoggingContext;
import minum.sampledomain.LruCacheTests;
import minum.security.TheBrigTests;
import minum.testing.RegexUtilsTests;
import minum.testing.TestFrameworkTests;
import minum.testing.TestLogger;
import minum.utils.*;
import minum.web.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

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
      MyThread.sleep(100);
      ex.printStackTrace();
    }
  }

  private final Constants constants;

  public Tests() {
    constants = new Constants();
  }

  private void indicateTestsFinished() {
    MyThread.sleep(20);
    System.out.println();
    System.out.println("-------------------------");
    System.out.println("----  Tests finished ----");
    System.out.println("-------------------------");
    System.out.println();
    System.out.println("See test report at out/reports/tests/tests.xml\n");
  }

  /**
   * These tests range in size from focusing on very small elements (unit tests)
   * to larger combinations of methods and classes (integration tests) but
   * stop short of running {@link FullSystem}.
   */
  private void unitAndIntegrationTests() throws Exception {
    Context context = buildContext();

    new WebTests(context).tests();
    new SimpleDatabaseTests(context).tests();
    new LruCacheTests(context).tests();
    new StringUtilsTests(context).tests();
    new TemplatingTests(context).tests();
    new FullSystemTests(context).tests();
    new StaticFilesCacheTests(context).tests();
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
    Context context2 = buildContextFunctionalTests();

    new FunctionalTests(context2).test();

    shutdownFunctionalTests(context2);
  }

  private Context buildContext() {
    ExecutorService es = ExtendedExecutor.makeExecutorService(constants);
    var loggingContext = new LoggingContext(es, constants);
    TestLogger logger = new TestLogger(loggingContext, "_unit_test_logger");
    loggingContext.setLogger(logger);
    var context = new Context(es, constants, null);
    context.setLogger(logger);
    return context;
  }

  private void handleShutdown(Context context) throws IOException {
    var logger2 = (TestLogger) context.getLogger();
    logger2.writeTestReport();
    FileUtils.deleteDirectoryRecursivelyIfExists(Path.of(constants.DB_DIRECTORY), logger2);
    new ActionQueueKiller(context).killAllQueues();
    context.getExecutorService().shutdownNow();
    context.getLogger().stop();
  }

  private Context buildContextFunctionalTests() {
    System.out.println("Starting a soup-to-nuts tests of the full system");

    ExecutorService es = ExtendedExecutor.makeExecutorService(constants);
    LoggingContext loggingContext = new LoggingContext(es, constants);
    TestLogger logger = new TestLogger(loggingContext, "integration_test_logger");

    var fs = FullSystem.initialize(logger, constants);
    Context context = fs.getContext();

    new TheRegister(context).registerDomains();
    return context;
  }

  private void shutdownFunctionalTests(Context context) throws IOException {
    FileUtils.deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().DB_DIRECTORY), context.getLogger());
    var fs2 = context.getFullSystem();
    fs2.removeShutdownHook();
    fs2.close();
    context.getExecutorService().shutdownNow();
    context.getLogger().stop();
  }

}

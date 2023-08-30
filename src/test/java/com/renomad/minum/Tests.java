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
   * Run a test of the entire system.  In particular, runs code
   * from {@link FullSystem}
   */
  private void testFullSystem_Soup_To_Nuts() throws Exception {
    Context context = buildContextFunctionalTests();
    new FunctionalTests(context).test();
    shutdownFunctionalTests(context);
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

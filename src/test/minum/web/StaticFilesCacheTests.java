package minum.web;

import minum.testing.TestLogger;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class StaticFilesCacheTests {
    private final TestLogger logger;

    public StaticFilesCacheTests(TestLogger logger) {
        this.logger = logger;
        logger.testSuite("StaticFilesCache Tests");
    }

    public void tests(ExecutorService es) throws IOException {

        logger.test("Happy path - load some static files into the cache"); {
            var staticFilesCache = new StaticFilesCache(logger);
            staticFilesCache.loadStaticFiles();
        }
    }
}

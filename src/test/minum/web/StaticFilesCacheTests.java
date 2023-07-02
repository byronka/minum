package minum.web;

import minum.TestContext;
import minum.testing.TestLogger;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class StaticFilesCacheTests {
    private final TestLogger logger;

    public StaticFilesCacheTests(TestContext context) {
        this.logger = context.getLogger();
        logger.testSuite("StaticFilesCache Tests", "StaticFilesCacheTests");
    }

    public void tests() throws IOException {

        logger.test("Happy path - load some static files into the cache"); {
            var staticFilesCache = new StaticFilesCache(logger);
            staticFilesCache.loadStaticFiles();
        }
    }
}

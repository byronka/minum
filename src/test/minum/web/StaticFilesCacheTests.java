package minum.web;

import minum.Context;
import minum.testing.TestLogger;

import java.io.IOException;

public class StaticFilesCacheTests {
    private final TestLogger logger;

    public StaticFilesCacheTests(Context context) {
        this.logger = (TestLogger) context.getLogger();
        logger.testSuite("StaticFilesCache Tests", "StaticFilesCacheTests");
    }

    public void tests() throws IOException {

        logger.test("Happy path - load some static files into the cache"); {
            var staticFilesCache = new StaticFilesCache(logger);
            staticFilesCache.loadStaticFiles();
        }
    }
}

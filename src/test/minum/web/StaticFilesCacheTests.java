package minum.web;

import minum.Context;
import minum.testing.TestLogger;

import java.io.IOException;
import java.nio.file.Path;

import static minum.testing.TestFramework.assertEquals;
import static minum.testing.TestFramework.assertThrows;

public class StaticFilesCacheTests {
    private final TestLogger logger;

    public StaticFilesCacheTests(Context context) {
        this.logger = (TestLogger) context.getLogger();
        logger.testSuite("StaticFilesCacheTests");
    }

    public void tests() throws IOException {

        var staticFilesCache = new StaticFilesCache(logger);

        logger.test("Happy path - load some static files into the cache"); {
            staticFilesCache.loadStaticFiles();
        }

        logger.test("If the static cache is given something that it can't handle, it throws an exception"); {
            var ex = assertThrows(RuntimeException.class, () -> staticFilesCache.createStaticFileResponse(Path.of("Foo"), new byte[0]));
            assertEquals(ex.getMessage(), "StaticFilesCache cannot handle this file: Foo");
        }
    }
}

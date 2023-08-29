package com.renomad.minum;

import com.renomad.minum.logging.TestLogger;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertTrue;

public class ConfigErrorTests {
    private final TestLogger logger;
    private final ExecutorService es;
    private final Context context;

    public ConfigErrorTests(Context context) {
        this.logger = (TestLogger) context.getLogger();
        this.es = context.getExecutorService();
        this.context = context;
        logger.testSuite("ConfigErrorTests");
    }

    public void tests() throws IOException {

        /*
        It's quite important that the user has a configuration file.
        We specify it to be named minum.config in the root directory.
         */
        logger.test("configuration missing"); {
            String configErrorMessage = ConfigErrorMessage.getConfigErrorMessage();
            assertTrue(configErrorMessage.contains("No properties file found at ./minum.config"));
            assertTrue(configErrorMessage.contains("****   Copy after this line -v    ****"));
            assertTrue(configErrorMessage.contains("The log levels are:"));
        }
    }
}
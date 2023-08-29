package com.renomad.minum;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.web.InputStreamUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import static com.renomad.minum.testing.TestFramework.*;

public class ConfigErrorTests {

    public ConfigErrorTests() {
    }

    /**
     * It's quite important that the user has a configuration file.
     * We specify it to be named minum.config in the root directory.
     */
    @Test
    public void testConfigurationMissing() {
        String configErrorMessage = ConfigErrorMessage.getConfigErrorMessage();
        assertTrue(configErrorMessage.contains("No properties file found at ./minum.config"));
        assertTrue(configErrorMessage.contains("****   Copy after this line -v    ****"));
        assertTrue(configErrorMessage.contains("The log levels are:"));
    }

}

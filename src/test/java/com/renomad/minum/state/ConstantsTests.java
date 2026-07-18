package com.renomad.minum.state;

import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.web.WebServerException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.List;
import java.util.Properties;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * The {@link Constants} class is where we store values that
 * should not change during the course of the program. You
 * might think that means it can be static, and you would
 * be right.  However, by making it an object, it does
 * give us some leeway in our testing - it frees us to
 * have multiple instances of the whole program running
 * with potentially varying constants.  That kind of
 * flexibility is not possible without making nearly
 * everything an instantiable class.
 */
public class ConstantsTests {


    static private Context context;
    static private TestLogger logger;

    @BeforeClass
    public static void init() {
        context = TestFramework.buildTestingContext("ConstantsTests");
        logger = (TestLogger)context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        TestFramework.shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    /**
     * There are a few "getProps" methods in the
     * Constants class, which are there to enable
     * converting strings to more sophisticated
     * types.
     * <br>
     * One in particular is the overload that returns
     * a list of strings.  Here, we'll test that it
     * behaves as expected.
     */
    @Test
    public void testGetProps_Array() {
        List<String> extraMimeMappings = Constants.extractList(" a,b, c, d, foo bar   , biz", "");
        assertEquals(extraMimeMappings, List.of("a","b","c","d", "foo bar","biz"));
        assertEquals(Constants.extractList("", ""), List.of(""));
    }

    /**
     * In your testing, it may be necessary to inject your own choice
     * of properties to the constants file.  In particular, it might be
     * common to set the root of the database, or the port of the server.
     */
    @Test
    public void testCustomProps() {
        Properties properties = new Properties();
        properties.setProperty("SERVER_PORT","1234");
        properties.setProperty("DB_DIRECTORY","/some/directory/here");
        var constants = new Constants(properties);
        assertEquals(constants.serverPort, 1234);
        assertEquals(constants.dbDirectory, "/some/directory/here");
        // if a property does not exist, it will use defaults
        assertEquals(constants.socketTimeoutMillis, 7000);
    }

    @Test
    public void testGettingConfiguredPropertiesFromFile_NothingFound() {
        Properties props = Constants.getConfiguredProperties("foo");
        assertEquals(props.size(), 0);
    }

    @Test
    public void test_convertLoggingStringsToEnums() {
        List<String> logLevels = List.of("DEBUG");
        List<LoggingLevel> result = Constants.convertLoggingStringsToEnums(logLevels);
        assertEquals(result.toString(), List.of(LoggingLevel.DEBUG).toString());
    }

    @Test
    public void test_convertLoggingStringsToEnums_NegativeCase() {
        List<String> logLevels = List.of("FUBAR");
        List<LoggingLevel> result = Constants.convertLoggingStringsToEnums(logLevels);
        assertEquals(result.toString(), List.of().toString());
    }

    @Test
    public void test_ServerPortConstant() {
        {
            var properties = new Properties();
            properties.setProperty("SERVER_PORT", "-2");
            var ex = assertThrows(WebServerException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "SERVER_PORT must be between -1 and 65,535.  Value was: -2");
        }
        {
            var properties = new Properties();
            properties.setProperty("SERVER_PORT", "-1");
            new Constants(properties);
        }
        {
            var properties = new Properties();
            properties.setProperty("SERVER_PORT", "0");
            new Constants(properties);
        }
        {
            var properties = new Properties();
            properties.setProperty("SERVER_PORT", "65535");
            new Constants(properties);
        }
        {
            var properties = new Properties();
            properties.setProperty("SERVER_PORT", "65536");
            var ex = assertThrows(WebServerException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "SERVER_PORT must be between -1 and 65,535.  Value was: 65536");
        }
    }

    @Test
    public void test_SecureServerPortConstant() {
        {
            var properties = new Properties();
            properties.setProperty("SSL_SERVER_PORT", "-2");
            var ex = assertThrows(WebServerException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "SSL_SERVER_PORT must be between -1 and 65,535.  Value was: -2");
        }
        {
            var properties = new Properties();
            properties.setProperty("SSL_SERVER_PORT", "-1");
            new Constants(properties);
        }
        {
            var properties = new Properties();
            properties.setProperty("SSL_SERVER_PORT", "0");
            new Constants(properties);
        }
        {
            var properties = new Properties();
            properties.setProperty("SSL_SERVER_PORT", "65535");
            new Constants(properties);
        }
        {
            var properties = new Properties();
            properties.setProperty("SSL_SERVER_PORT", "65536");
            var ex = assertThrows(WebServerException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "SSL_SERVER_PORT must be between -1 and 65,535.  Value was: 65536");
        }
    }

    @Test
    public void test_HostNameConstant() {
        {
            var properties = new Properties();
            properties.setProperty("HOST_NAME", "");
            var ex = assertThrows(WebServerException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "HOST_NAME must be a non-empty string");
        }
        {
            var properties = new Properties();
            properties.setProperty("HOST_NAME", "   ");
            var ex = assertThrows(WebServerException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "HOST_NAME must be a non-empty string");
        }
    }

    @Test
    public void test_maxElementsLruCacheStaticFiles() {
        {
            var properties = new Properties();
            properties.setProperty("MAX_ELEMENTS_LRU_CACHE_STATIC_FILES", "");
            var ex = assertThrows(NumberFormatException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "For input string: \"\"");
        }
        {
            var properties = new Properties();
            properties.setProperty("MAX_ELEMENTS_LRU_CACHE_STATIC_FILES", "0");
            var ex = assertThrows(WebServerException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "MAX_ELEMENTS_LRU_CACHE_STATIC_FILES must be a positive non-zero value.  Value was: 0");
        }
        {
            var properties = new Properties();
            properties.setProperty("MAX_ELEMENTS_LRU_CACHE_STATIC_FILES", "-1");
            var ex = assertThrows(WebServerException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "MAX_ELEMENTS_LRU_CACHE_STATIC_FILES must be a positive non-zero value.  Value was: -1");
        }
        {
            var properties = new Properties();
            properties.setProperty("MAX_ELEMENTS_LRU_CACHE_STATIC_FILES", String.valueOf(Long.MAX_VALUE));
            var ex = assertThrows(ArithmeticException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "integer overflow");
        }
    }

    @Test
    public void testSocketTimeoutMillis() {
        {
            var properties = new Properties();
            properties.setProperty("SOCKET_TIMEOUT_MILLIS", "0");
            new Constants(properties);
        }
        {
            var properties = new Properties();
            properties.setProperty("SOCKET_TIMEOUT_MILLIS", "-1");
            var ex = assertThrows(WebServerException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "SOCKET_TIMEOUT_MILLIS must be a positive value.  Value was: -1");
        }
        {
            var properties = new Properties();
            properties.setProperty("SOCKET_TIMEOUT_MILLIS", String.valueOf(Long.MAX_VALUE));
            var ex = assertThrows(ArithmeticException.class, () -> new Constants(properties));
            assertEquals(ex.getMessage(), "integer overflow");
        }
    }

}

package com.renomad.minum.state;

import com.renomad.minum.logging.LoggingLevel;
import org.junit.Test;

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
}

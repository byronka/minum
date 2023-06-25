package minum;

import minum.web.FullSystem;

import java.util.Arrays;
import java.util.List;

/**
 * Very important system design decisions are made here.  All
 * developers on this project should look through each of these.
 */
public class Constants {

    /**
     * The port for our regular, non-encrypted server
     */
    public static final int SERVER_PORT = getProp("SERVER_PORT",  8080);

    /**
     * The port for our encrypted server
     */
    public static final int SECURE_SERVER_PORT = getProp("SERVER_PORT",  8443);

    /**
     * This is returned as the "host:" attribute in an HTTP request
     */
    public static final String HOST_NAME = FullSystem.getConfiguredProperties().getProperty("HOST_NAME",  "localhost");

    /**
     * This is the root directory of our database
     */
    public static final String DB_DIRECTORY = FullSystem.getConfiguredProperties().getProperty("DB_DIRECTORY",  "db");

    /**
     * The default logging levels
     */
    public static final List<String> LOG_LEVELS = getProp("LOG_LEVELS", "DEBUG,TRACE,ASYNC_ERROR,AUDIT");

    /**
     * If true, use the new Java "Project Loom" virtual threads instead of
     * regular threads.
     */
    public static final boolean USE_VIRTUAL = getProp("USE_VIRTUAL", false);

    /**
     * The path to the keystore, required for encrypted TLS communication
     */
    public static final String KEYSTORE_PATH = FullSystem.getConfiguredProperties().getProperty("KEYSTORE_PATH",  "");

    /**
     * The password of the keystore, used for TLS
     */
    public static final String KEYSTORE_PASSWORD = FullSystem.getConfiguredProperties().getProperty("KEYSTORE_PASSWORD",  "");


    /**
     * If true, any requests to the non-encrypted port will receive a
     * redirect to the secure schema.  This is mostly used in production
     * environments and is thus defaulted to false.
     */
    public static final boolean REDIRECT_80 = getProp("REDIRECT_80", false);

    /**
     * this is the most bytes we'll read from a socket
     */
    public static final int MAX_READ_SIZE_BYTES = getProp("MAX_READ_SIZE_BYTES",  10 * 1024 * 1024);

    /**
     * The most bytes we'll read as a single line
     */
    public static final int MAX_READ_LINE_SIZE_BYTES = getProp("MAX_READ_LINE_SIZE_BYTES", 200);

    /**
     * A user can only provide up to this many query string keys
     */
    public static final int MAX_QUERY_STRING_KEYS_COUNT = getProp("MAX_QUERY_STRING_KEYS_COUNT", 20);

    /**
     * Totally nonsense if we find more than this many matches of cookies in the headers.
     */
    public static final int MOST_COOKIES_WELL_LOOK_THROUGH = getProp("MOST_COOKIES_WELL_LOOK_THROUGH", 5);

    /**
     * We'll only read this many headers off a message.  Anything more is bonkers / hacking.
     */
    public static final int MAX_HEADERS_COUNT = getProp("MAX_HEADERS_COUNT", 70);

    /**
     * We have a tokenizer that can split a string into partitions.  When
     * would we ever need it to split this many?
     */
    public static final int MAX_TOKENIZER_PARTITIONS = getProp("MAX_TOKENIZER_PARTITIONS", 20);

    /**
     * How long will we let a socket live before we crash it closed?
     * See {@link java.net.Socket#setSoTimeout(int)}
     */
    public static final int SOCKET_TIMEOUT_MILLIS = getProp("SOCKET_TIMEOUT_MILLIS", 3 * 1000);

    /**
     * If a client does something that we consider an indicator for attacking, put them in
     * jail for a longer duration.
     */
    public static final int VULN_SEEKING_JAIL_DURATION = getProp("VULN_SEEKING_JAIL_DURATION", 7 * 24 * 60 * 60 * 1000);

    /**
     * TheBrig is what puts client ip's in jail, if we feel they are attacking us.
     * If this is disabled, that functionality is removed.
     */
    public static final boolean IS_THE_BRIG_ENABLED = getProp("theBrigEnabled", false);

    /**
     * These are a list of error messages that often indicate unusual behavior, maybe an attacker
     */
    public static final List<String> SUSPICIOUS_ERRORS = getProp("SUSPICIOUS_ERRORS", "");

    /**
     * These are a list of paths that often indicate unusual behavior, maybe an attacker
     */
    public static final List<String> SUSPICIOUS_PATHS = getProp("SUSPICIOUS_PATHS", "");

    /**
     * A helper method to remove some redundant boilerplate code for grabbing
     * configuration values from app.config
     */
    private static int getProp(String propName, int propDefault) {
        return Integer.parseInt(FullSystem.getConfiguredProperties().getProperty(propName, String.valueOf(propDefault)));
    }

    /**
     * A helper method to remove some redundant boilerplate code for grabbing
     * configuration values from app.config
     */
    private static boolean getProp(String propName, boolean propDefault) {
        return Boolean.parseBoolean(FullSystem.getConfiguredProperties().getProperty(propName, String.valueOf(propDefault)));
    }

    /**
     * A helper method to remove some redundant boilerplate code for grabbing
     * configuration values from app.config
     */
    private static List<String> getProp(String propName, String propDefault) {
        return Arrays.asList(FullSystem.getConfiguredProperties().getProperty(propName, propDefault).split(","));
    }

}




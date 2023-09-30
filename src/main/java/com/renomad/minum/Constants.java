package com.renomad.minum;

import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.utils.TimeUtils;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Very important system design decisions are made here.  All
 * developers on this project should look through each of these.
 */
public final class Constants {

    private Properties properties;

    public Constants() {
        properties = getConfiguredProperties();

        SERVER_PORT = getProp("SERVER_PORT",  8080);
        SECURE_SERVER_PORT = getProp("SSL_SERVER_PORT",  8443);
        HOST_NAME = properties.getProperty("HOST_NAME",  "localhost");
        DB_DIRECTORY = properties.getProperty("DB_DIRECTORY",  "db");
        STATIC_FILES_DIRECTORY = properties.getProperty("STATIC_FILES_DIRECTORY",  "static");
        LOG_LEVELS = convertLoggingStringsToEnums(getProp("LOG_LEVELS", "DEBUG,TRACE,ASYNC_ERROR,AUDIT"));
        USE_VIRTUAL = getProp("USE_VIRTUAL", false);
        KEYSTORE_PATH = properties.getProperty("KEYSTORE_PATH",  "");
        KEYSTORE_PASSWORD = properties.getProperty("KEYSTORE_PASSWORD",  "");
        REDIRECT_TO_SECURE = getProp("REDIRECT_TO_SECURE", false);
        MAX_READ_SIZE_BYTES = getProp("MAX_READ_SIZE_BYTES",  10 * 1024 * 1024);
        MAX_READ_LINE_SIZE_BYTES = getProp("MAX_READ_LINE_SIZE_BYTES", 1024);
        MAX_QUERY_STRING_KEYS_COUNT = getProp("MAX_QUERY_STRING_KEYS_COUNT", 20);
        MOST_COOKIES_WELL_LOOK_THROUGH = getProp("MOST_COOKIES_WELL_LOOK_THROUGH", 5);
        MAX_HEADERS_COUNT = getProp("MAX_HEADERS_COUNT", 70);
        MAX_TOKENIZER_PARTITIONS = getProp("MAX_TOKENIZER_PARTITIONS", 20);
        SOCKET_TIMEOUT_MILLIS = getProp("SOCKET_TIMEOUT_MILLIS", 7 * 1000);
        KEEP_ALIVE_TIMEOUT_SECONDS = getProp("KEEP_ALIVE_TIMEOUT_SECONDS", 3);
        VULN_SEEKING_JAIL_DURATION = getProp("VULN_SEEKING_JAIL_DURATION", 10 * 1000);
        IS_THE_BRIG_ENABLED = getProp("IS_THE_BRIG_ENABLED", false);
        SUSPICIOUS_ERRORS = getProp("SUSPICIOUS_ERRORS", "");
        SUSPICIOUS_PATHS = getProp("SUSPICIOUS_PATHS", "");
        START_TIME = System.currentTimeMillis();
        EXTRA_MIME_MAPPINGS = getProp("EXTRA_MIME_MAPPINGS", "");
        STATIC_FILE_CACHE_TIME = getProp("STATIC_FILE_CACHE_TIME", 60 * 5);
        USE_CACHE_FOR_STATIC_FILES = getProp("USE_CACHE_FOR_STATIC_FILES", true);
        MAX_ELEMENTS_LRU_CACHE_STATIC_FILES = getProp("MAX_ELEMENTS_LRU_CACHE_STATIC_FILES", 1000);
    }

    /**
     * The port for our regular, non-encrypted server
     */
    public final int SERVER_PORT;

    /**
     * The port for our encrypted server
     */
    public final int SECURE_SERVER_PORT;

    /**
     * This is returned as the "host:" attribute in an HTTP request
     */
    public final String HOST_NAME;

    /**
     * This is the root directory of our database
     */
    public final String DB_DIRECTORY;

    /**
     * Root directory of static files
     */
    public final String STATIC_FILES_DIRECTORY;

    /**
     * The default logging levels
     */
    public final List<LoggingLevel> LOG_LEVELS;

    /**
     * If true, use the new Java "Project Loom" virtual threads instead of
     * regular threads.
     */
    public final boolean USE_VIRTUAL;

    /**
     * The path to the keystore, required for encrypted TLS communication
     */
    public final String KEYSTORE_PATH;

    /**
     * The password of the keystore, used for TLS
     */
    public final String KEYSTORE_PASSWORD;


    /**
     * If true, any requests to the non-encrypted port will receive a
     * redirect to the secure schema.  This is mostly used in production
     * environments and is thus defaulted to false.
     */
    public final boolean REDIRECT_TO_SECURE;

    /**
     * this is the most bytes we'll read from a socket
     */
    public final int MAX_READ_SIZE_BYTES;

    /**
     * The most bytes we'll read as a single line.
     * Could be pretty large, for example, I have seen cases where
     * the cookies being sent, like on localhost, could
     * be in excess of 500 bytes.
     */
    public final int MAX_READ_LINE_SIZE_BYTES;

    /**
     * A user can only provide up to this many query string keys
     */
    public final int MAX_QUERY_STRING_KEYS_COUNT;

    /**
     * Totally nonsense if we find more than this many matches of cookies in the headers.
     */
    public final int MOST_COOKIES_WELL_LOOK_THROUGH;

    /**
     * We'll only read this many headers off a message.  Anything more is bonkers / hacking.
     */
    public final int MAX_HEADERS_COUNT;

    /**
     * We have a tokenizer that can split a string into partitions.  When
     * would we ever need it to split this many?
     */
    public final int MAX_TOKENIZER_PARTITIONS;

    /**
     * How long will we let a socket live before we crash it closed?
     * See {@link java.net.Socket#setSoTimeout(int)}
     */
    public final int SOCKET_TIMEOUT_MILLIS;

    /**
     * We include this value in the keep-alive header. It lets the
     * browser know how long to hold the socket open, in seconds,
     * before it decides we aren't sending anything else and closes it.
     */
    public final int KEEP_ALIVE_TIMEOUT_SECONDS;

    /**
     * If a client does something that we consider an indicator for attacking, put them in
     * jail for a longer duration.
     */
    public final int VULN_SEEKING_JAIL_DURATION;

    /**
     * TheBrig is what puts client ip's in jail, if we feel they are attacking us.
     * If this is disabled, that functionality is removed.
     */
    public final boolean IS_THE_BRIG_ENABLED;

    /**
     * These are a list of error messages that often indicate unusual behavior, maybe an attacker
     */
    public final List<String> SUSPICIOUS_ERRORS;

    /**
     * These are a list of paths that often indicate unusual behavior, maybe an attacker
     */
    public final List<String> SUSPICIOUS_PATHS;

    /**
     * This value is the result of running System.currentTimeMillis() when this
     * class gets instantiated, and that is done at the very beginning.
     */
    public final long START_TIME;

    /**
     * These are key-value pairs for mappings between a file
     * suffix and a mime type.
     * <p>
     *     These are read by our system in the StaticFilesCache
     *     as key-1,value-1,key-2,value-2,... and so on.
     * </p>
     * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types
     *
     */
    public final List<String> EXTRA_MIME_MAPPINGS;

    /**
     * Length of time, in seconds, for static files to be cached,
     * per the provisions of the Cache-Control header, e.g.
     * <pre>
     *     {@code Cache-Control: max-age=300}
     * </pre>
     */
    public final long STATIC_FILE_CACHE_TIME;

    /**
     * Whether we will use caching for the static files.
     * <p>
     *     When a user requests a path we don't recognize, we
     *     go looking for it using {@link com.renomad.minum.utils.FileUtils#readStaticFile(String)}.
     *     If we have already found it for someone else, it will
     *     be in a cache.
     * </p>
     * <p>
     *     However, if we are doing development, it helps to
     *     not have caching enabled - it can confuse.
     * </p>
     */
    public final boolean USE_CACHE_FOR_STATIC_FILES;

    /**
     * This constant controls the maximum number of elements for the {@link com.renomad.minum.utils.LRUCache}
     * we create for use by {@link com.renomad.minum.utils.FileUtils}. As files are read
     * by FileUtil's methods, they will be stored in this cache, to avoid reading from
     * disk.  However, caching can certainly complicate things, so if you would prefer
     * not to store these values in a cache, set {@link USE_CACHE_FOR_STATIC_FILES} to false.
     * <p>
     *     The unit here is the number of elements to store in the cache.  Be aware: elements
     *     can be of any size, so two caches each having a max size of 1000 elements could be
     *     drastically different sizes.
     * </p>
     */
    public final int MAX_ELEMENTS_LRU_CACHE_STATIC_FILES;

    /**
     * A helper method to remove some redundant boilerplate code for grabbing
     * configuration values from minum.config
     */
    private int getProp(String propName, int propDefault) {
        return Integer.parseInt(properties.getProperty(propName, String.valueOf(propDefault)));
    }

    /**
     * A helper method to remove some redundant boilerplate code for grabbing
     * configuration values from minum.config
     */
    private boolean getProp(String propName, boolean propDefault) {
        return Boolean.parseBoolean(properties.getProperty(propName, String.valueOf(propDefault)));
    }

    /**
     * A helper method to remove some redundant boilerplate code for grabbing
     * configuration values from minum.config
     */
    private List<String> getProp(String propName, String propDefault) {
        String propValue = properties.getProperty(propName);
        if (propValue == null) {
            if (propDefault.isBlank()) {
                return List.of();
            } else {
                return Arrays.asList(propDefault.split(","));
            }
        } else {
            return Arrays.asList(propValue.split(","));
        }
    }


    /**
     * This overload allows you to specify that the contents of the
     * properties file should be shown when it's read.
     */
    private Properties getConfiguredProperties() {
        var props = new Properties();
        String fileName = "minum.config";
        try (FileInputStream fis = new FileInputStream(fileName)) {
            System.out.println(TimeUtils.getTimestampIsoInstant() +
                    " found properties file at ./minum.config.  Loading properties");
            props.load(fis);
        } catch (Exception ex) {
            System.out.println(ConfigErrorMessage.getConfigErrorMessage());
        }
        return props;
    }


    /**
     * Given a list of strings representing logging levels,
     * convert it to a list of enums.  Log levels are enumerated
     * in {@link LoggingLevel}.
     */
    private List<LoggingLevel> convertLoggingStringsToEnums(List<String> logLevels) {
        List<String> logLevelStrings = logLevels.stream().map(String::toUpperCase).toList();
        List<LoggingLevel> enabledLoggingLevels = new ArrayList<>();
        for (LoggingLevel t : LoggingLevel.values()) {
            if (logLevelStrings.contains(t.name())) {
                enabledLoggingLevels.add(t);
            }
        }
        return enabledLoggingLevels;
    }
}




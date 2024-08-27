package com.renomad.minum.state;

import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.utils.TimeUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Very important system design decisions are made here.  All
 * developers on this project should look through each of these.
 */
public final class Constants {

    private final Properties properties;

    public Constants() {
        this(null);
    }

    public Constants(Properties props) {
        properties = Objects.requireNonNullElseGet(props, Constants::getConfiguredProperties);

        serverPort = getProp("SERVER_PORT",  8080);
        secureServerPort = getProp("SSL_SERVER_PORT",  8443);
        hostName = properties.getProperty("HOST_NAME",  "localhost");
        dbDirectory = properties.getProperty("DB_DIRECTORY",  "db");
        staticFilesDirectory = properties.getProperty("STATIC_FILES_DIRECTORY",  "static");
        logLevels = convertLoggingStringsToEnums(getProp("LOG_LEVELS", "DEBUG,TRACE,ASYNC_ERROR,AUDIT"));
        keystorePath = properties.getProperty("KEYSTORE_PATH",  "");
        keystorePassword = properties.getProperty("KEYSTORE_PASSWORD",  "");
        maxReadSizeBytes = getProp("MAX_READ_SIZE_BYTES",  10 * 1024 * 1024);
        maxReadLineSizeBytes = getProp("MAX_READ_LINE_SIZE_BYTES",  1024);
        socketTimeoutMillis = getProp("SOCKET_TIMEOUT_MILLIS", 7 * 1000);
        keepAliveTimeoutSeconds = getProp("KEEP_ALIVE_TIMEOUT_SECONDS", 3);
        vulnSeekingJailDuration = getProp("VULN_SEEKING_JAIL_DURATION", 10 * 1000);
        isTheBrigEnabled = getProp("IS_THE_BRIG_ENABLED", true);
        suspiciousErrors = getProp("SUSPICIOUS_ERRORS", "");
        suspiciousPaths = getProp("SUSPICIOUS_PATHS", "");
        startTime = System.currentTimeMillis();
        extraMimeMappings = getProp("EXTRA_MIME_MAPPINGS", "");
        staticFileCacheTime = getProp("STATIC_FILE_CACHE_TIME", 60 * 5);
        useCacheForStaticFiles = getProp("USE_CACHE_FOR_STATIC_FILES", true);
        maxElementsLruCacheStaticFiles = getProp("MAX_ELEMENTS_LRU_CACHE_STATIC_FILES", 1000);
    }

    /**
     * The port for our regular, non-encrypted server
     */
    public final int serverPort;

    /**
     * The port for our encrypted server
     */
    public final int secureServerPort;

    /**
     * This is returned as the "host:" attribute in an HTTP request
     */
    public final String hostName;

    /**
     * This is the root directory of our database
     */
    public final String dbDirectory;

    /**
     * Root directory of static files
     */
    public final String staticFilesDirectory;

    /**
     * The default logging levels
     */
    public final List<LoggingLevel> logLevels;

    /**
     * The path to the keystore, required for encrypted TLS communication
     */
    public final String keystorePath;

    /**
     * The password of the keystore, used for TLS
     */
    public final String keystorePassword;

    /**
     * this is the most bytes we'll read while parsing the Request body
     */
    public final int maxReadSizeBytes;

    /**
     * this is the most bytes we'll read on a single line, when reading by
     * line.  This is especially relevant when reading headers and request lines, which
     * can bulk up with jwt's or query strings, respectively.
     */
    public final int maxReadLineSizeBytes;

    /**
     * How long will we let a socket live before we crash it closed?
     * See {@link java.net.Socket#setSoTimeout(int)}
     */
    public final int socketTimeoutMillis;

    /**
     * We include this value in the keep-alive header. It lets the
     * browser know how long to hold the socket open, in seconds,
     * before it decides we aren't sending anything else and closes it.
     */
    public final int keepAliveTimeoutSeconds;

    /**
     * If a client does something that we consider an indicator for attacking, put them in
     * jail for a longer duration.
     */
    public final int vulnSeekingJailDuration;

    /**
     * TheBrig is what puts client ip's in jail, if we feel they are attacking us.
     * If this is disabled, that functionality is removed.
     */
    public final boolean isTheBrigEnabled;

    /**
     * These are a list of error messages that often indicate unusual behavior, maybe an attacker
     */
    public final List<String> suspiciousErrors;

    /**
     * These are a list of paths that often indicate unusual behavior, maybe an attacker
     */
    public final List<String> suspiciousPaths;

    /**
     * This value is the result of running System.currentTimeMillis() when this
     * class gets instantiated, and that is done at the very beginning.
     */
    public final long startTime;

    /**
     * These are key-value pairs for mappings between a file
     * suffix and a mime type.
     * <p>
     *     These are read by our system in the StaticFilesCache
     *     as key-1,value-1,key-2,value-2,... and so on.
     * </p>
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types">Basics of HTTP</a>
     *
     */
    public final List<String> extraMimeMappings;

    /**
     * Length of time, in seconds, for static files to be cached,
     * per the provisions of the Cache-Control header, e.g.
     * <pre>
     *     {@code Cache-Control: max-age=300}
     * </pre>
     */
    public final long staticFileCacheTime;

    /**
     * Whether we will use caching for the static files.
     * <p>
     *     When a user requests a path we don't recognize, we
     *     go looking for it.
     *     If we have already found it for someone else, it will
     *     be in a cache.
     * </p>
     * <p>
     *     However, if we are doing development, it helps to
     *     not have caching enabled - it can confuse.
     * </p>
     */
    public final boolean useCacheForStaticFiles;

    /**
     * This constant controls the maximum number of elements for the {@link com.renomad.minum.utils.LRUCache}
     * we create for use by {@link com.renomad.minum.utils.FileUtils}. As files are read
     * by FileUtil's methods, they will be stored in this cache, to avoid reading from
     * disk.  However, caching can certainly complicate things, so if you would prefer
     * not to store these values in a cache, set {@link #useCacheForStaticFiles} to false.
     * <p>
     *     The unit here is the number of elements to store in the cache.  Be aware: elements
     *     can be of any size, so two caches each having a max size of 1000 elements could be
     *     drastically different sizes.
     * </p>
     */
    public final int maxElementsLruCacheStaticFiles;

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
        return extractList(propValue, propDefault);
    }

    /**
     * Extract a list out of a comma-delimited string.
     * <br>
     * Example: a,b, c, d -> List.of("a","b","c","d")
     * @param propValue the value of a property
     * @param propDefault the default value to use, if the propValue is null
     */
    static List<String> extractList(String propValue, String propDefault) {
        if (propValue == null) {
            if (propDefault.isBlank()) {
                return List.of();
            } else {
                return Arrays.asList(propDefault.trim().split("\\s*,\\s*"));
            }
        } else {
            return Arrays.asList(propValue.trim().split("\\s*,\\s*"));
        }
    }

    public static Properties getConfiguredProperties() {
        return getConfiguredProperties("minum.config");
    }

    /**
     * Reads properties from minum.config
     */
    static Properties getConfiguredProperties(String fileName) {
        var props = new Properties();
        try (FileInputStream fis = new FileInputStream(fileName)) {
            System.out.println(TimeUtils.getTimestampIsoInstant() +
                    " found properties file at ./minum.config.  Loading properties");
            props.load(fis);
        } catch (IOException ex) {
            System.out.println(CONFIG_ERROR_MESSAGE);
        }
        return props;
    }


    /**
     * Given a list of strings representing logging levels,
     * convert it to a list of enums.  Log levels are enumerated
     * in {@link LoggingLevel}.
     */
    static List<LoggingLevel> convertLoggingStringsToEnums(List<String> logLevels) {
        List<String> logLevelStrings = logLevels.stream().map(String::toUpperCase).toList();
        List<LoggingLevel> enabledLoggingLevels = new ArrayList<>();
        for (LoggingLevel t : LoggingLevel.values()) {
            if (logLevelStrings.contains(t.name())) {
                enabledLoggingLevels.add(t);
            }
        }
        return enabledLoggingLevels;
    }

    private static final String CONFIG_ERROR_MESSAGE = """
                
                
                
                ----------------------------------------------------------------
                ----------------- System Configuration Missing -----------------
                ----------------------------------------------------------------
                
                No properties file found at ./minum.config
                
                Continuing, using defaults.  See source code for Minum for an
                example minum.config, which will allow you to customize behavior.
                
                ----------------------------------------------------------------
                ----------------------------------------------------------------
                """;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Constants constants = (Constants) o;
        return serverPort == constants.serverPort && secureServerPort == constants.secureServerPort && maxReadSizeBytes == constants.maxReadSizeBytes && maxReadLineSizeBytes == constants.maxReadLineSizeBytes && socketTimeoutMillis == constants.socketTimeoutMillis && keepAliveTimeoutSeconds == constants.keepAliveTimeoutSeconds && vulnSeekingJailDuration == constants.vulnSeekingJailDuration && isTheBrigEnabled == constants.isTheBrigEnabled && startTime == constants.startTime && staticFileCacheTime == constants.staticFileCacheTime && useCacheForStaticFiles == constants.useCacheForStaticFiles && maxElementsLruCacheStaticFiles == constants.maxElementsLruCacheStaticFiles && Objects.equals(properties, constants.properties) && Objects.equals(hostName, constants.hostName) && Objects.equals(dbDirectory, constants.dbDirectory) && Objects.equals(staticFilesDirectory, constants.staticFilesDirectory) && Objects.equals(logLevels, constants.logLevels) && Objects.equals(keystorePath, constants.keystorePath) && Objects.equals(keystorePassword, constants.keystorePassword) && Objects.equals(suspiciousErrors, constants.suspiciousErrors) && Objects.equals(suspiciousPaths, constants.suspiciousPaths) && Objects.equals(extraMimeMappings, constants.extraMimeMappings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties, serverPort, secureServerPort, hostName, dbDirectory, staticFilesDirectory, logLevels, keystorePath, keystorePassword, maxReadSizeBytes, maxReadLineSizeBytes, socketTimeoutMillis, keepAliveTimeoutSeconds, vulnSeekingJailDuration, isTheBrigEnabled, suspiciousErrors, suspiciousPaths, startTime, extraMimeMappings, staticFileCacheTime, useCacheForStaticFiles, maxElementsLruCacheStaticFiles);
    }
}




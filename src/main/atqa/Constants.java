package atqa;

import atqa.web.FullSystem;

/**
 * Very important system design decisions are made here.  All
 * developers on this project should look through each of these.
 */
public class Constants {
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

    private static int getProp(String propName, int propDefault) {
        return Integer.parseInt(FullSystem.getConfiguredProperties().getProperty(propName, String.valueOf(propDefault)));
    }
}




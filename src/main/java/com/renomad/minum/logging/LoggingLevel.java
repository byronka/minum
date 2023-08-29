package com.renomad.minum.logging;

/**
 * An enumeration of the levels of logging our system provides.
 */
public enum LoggingLevel {

    /**
     * Information useful for debugging.
     */
    DEBUG,

    /**
     * Represents an error that occurs in a separate thread, so
     * that we are not able to catch it bubbling up
     */
    ASYNC_ERROR,

    /**
     * Information marked as trace is pretty much entered for
     * the same reason as DEBUG - i.e. so we can see important
     * information about the running state of the program. The
     * only difference is that trace information is very voluminous.
     * That is, there's tons of it, and it could make it harder
     * to find the important information amongst a lot of noise.
     * For that reason, TRACE is usually turned off.
     */
    TRACE,

    /**
     * Information marked audit is for business-related stuff.  Like,
     * a new user being created.  A photo being looked for.  Stuff
     * closer to the user needs.
     */
    AUDIT
}

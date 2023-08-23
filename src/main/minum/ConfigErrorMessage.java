package minum;

/**
 * If the user has no configuration file, this class
 * contains code that will run to assist them.
 */
final class ConfigErrorMessage {

    private ConfigErrorMessage() {
        // making this private to be clearer it isn't supposed to be instantiated.
    }

    private static String getDefaultConfig() {
        return """
                ###
                ### The following was generated as a stop-gap measure because the
                ### mandatory config file was missing.  Please see the documentation
                ### for including a more proper configuration file.  This version
                ### lacks some fine-tuning keys.
                
                
                ###  The port used for our plain, non-secure server

                #SERVER_PORT=8080

                #-----------------------------

                ###  the port for our secure, TLS 1.3 server

                #SECURE_SERVER_PORT=8443

                #-----------------------------

                ###  the name of our host on the internet.  Used by our
                ###  system when it needs to build a self-referencing URL.

                #HOST_NAME=localhost

                ###  database top-level directory

                #DB_DIRECTORY=out/simple_db

                #-----------------------------

                ###  The log levels are:
                ###
                ###  Related to the business purposes of the application.  That is,
                ###  the very highest-level, perhaps least-common logging messages.
                ###  AUDIT
                ###
                ###  Information useful for debugging.
                ###  DEBUG
                ###
                ###
                ###  Represents an error that occurs in a separate thread, so
                ###  that we are not able to catch it bubbling up
                ###  ASYNC_ERROR
                ###
                ###
                ###  Information marked as trace is pretty much entered for
                ###  the same reason as DEBUG - i.e. so we can see important
                ###  information about the running state of the program. The
                ###  only difference is that trace information is very voluminous.
                ###  That is, there's tons of it, and it could make it harder
                ###  to find the important information amongst a lot of noise.
                ###  For that reason, TRACE is usually turned off.
                ###  TRACE
                ###
                ###  list them here, separated by commas

                #LOG_LEVELS=DEBUG,TRACE,ASYNC_ERROR,AUDIT

                """.stripLeading();
    }

    /**
     * If the user has no app.config file in the location they
     * run the software, this message will be shown to assist. It
     * is required that they have a configuration file, to make it
     * explicit where the "knobs and dials" of the application are
     * located.
     */
    static String getConfigErrorMessage() {
        return """
                
                
                
                ----------------------------------------------------------------
                ----------------- System misconfiguration ----------------------
                ----------------------------------------------------------------
                
                No properties file found at ./app.config
                
                A file named app.config with the following contents must exist in
                the directory you were at when running this program. Copy the
                following text and save it as a file called app.config at that
                location.
                
                ----------------------------------------------------------------
                ----------------------------------------------------------------
                
                    ****   Copy after this line -v    ****
                
                """ +
                ConfigErrorMessage.getDefaultConfig() + """
                
                    ****   Copy before this line -^    ****
                """;
    }
}

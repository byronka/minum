package com.renomad.minum;

/**
 * If the user has no configuration file, this class
 * contains code that will run to assist them.
 */
final class ConfigErrorMessage {

    private ConfigErrorMessage() {
        // making this private to be clearer it isn't supposed to be instantiated.
    }

    /**
     * If the user has no minum.config file in the location they
     * run the software, this message will be shown to assist. It
     * is required that they have a configuration file, to make it
     * explicit where the "knobs and dials" of the application are
     * located.
     */
    static String getConfigErrorMessage() {
        return """
                
                
                
                ----------------------------------------------------------------
                ----------------- System Configuration Missing -----------------
                ----------------------------------------------------------------
                
                No properties file found at ./minum.config
                
                Continuing, using defaults.  See source code for Minum for an
                example minum.config, which will allow you to customize behavior.
                
                ----------------------------------------------------------------
                ----------------------------------------------------------------
                """;
    }
}

package minum;

import minum.logging.ILogger;

import java.util.concurrent.ExecutorService;

/**
 * In order to avoid statics or singletons allow certain objects to be widely
 * available, we'll store them in this class.
 * @param logger the logger we'll use throughout the system
 * @param executorService the code which controls threads
 * @param constants constants that apply throughout the code, configurable by the
 *                  user in the app.config file.
 * @param fullSystem the code which kicks off many of the core functions of
 *                   the application and maintains oversight on those objects.
 */
public record Context(ILogger logger, ExecutorService executorService, Constants constants, FullSystem fullSystem) {
}

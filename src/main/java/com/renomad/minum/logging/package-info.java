/**
 * These classes enable outputting messages during the program run, labeled to indicate
 * the category.  It is able to do this without slowing down the system unduly.  Start
 * by reviewing {@link com.renomad.minum.logging.ILogger}
 * <p>
 *     <em>Examples:</em>
 * </p>
 * <pre>
 *     {@code
 *     this.logger = context.getLogger();
 *
 *     logger.logDebug(() -> "an empty path was provided to writeString");
 *
 *     logger.logTrace(() -> String.format("client connected from %s", sw.getRemoteAddrWithPort()));
 *
 *     logger.logAsyncError(() -> String.format("Error while reading file: %s. %s", path, StacktraceUtils.stackTraceToString(e)));
 *
 *     logger.logAudit(() -> String.format("%s has posted a new video, %s, with short description of %s, size of %d",
 *                authResult.user().getUsername(),
 *                newFilename,
 *                shortDescription,
 *                countOfVideoBytes
 *        ));
 *     }
 * </pre>
 */
package com.renomad.minum.logging;
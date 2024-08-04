package com.renomad.minum.sampledomain.auth;

import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * This class starts an infinite loop when the application begins,
 * reviewing the users and sessions.
 * <br>
 * Each user may have an optional session, recorded in their data.
 * We grab all those values, and compare that to what is in the total list
 * of sessions.  Sessions that aren't bound to a user will get deleted.
 */
public class LoopingSessionReviewing {

    private final ExecutorService es;
    private final ILogger logger;
    private final int sleepTime;
    private final AuthUtils au;
    private final Constants constants;
    private Thread myThread;

    public LoopingSessionReviewing(Context context, AuthUtils au) {
        this.es = context.getExecutorService();
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        this.au = au;
        this.sleepTime = 60 * 60 * 1000;
    }

    /**
     * This kicks off the infinite loop examining the session table
     */
    // Regarding the BusyWait - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @SuppressWarnings({"BusyWait"})
    public LoopingSessionReviewing initialize() {
        logger.logDebug(() -> "Initializing LoopingSessionReviewing main loop");
        Callable<Object> innerLoopThread = () -> {
            Thread.currentThread().setName("LoopingSessionReviewing");
            myThread = Thread.currentThread();
            while (true) {
                try {
                    List<User> users = au.getUsers();
                    List<SessionId> sessions = au.getSessions();
                    var sessionsToKill = determineSessionsToKill(users, sessions);
                    for (SessionId s : sessionsToKill) {
                        au.deleteSession(s);
                    }
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {

                    /*
                    this is what we expect to happen.
                    once this happens, we just continue on.
                    this only gets called when we are trying to shut everything
                    down cleanly
                     */

                    if (constants.logLevels.contains(LoggingLevel.DEBUG)) System.out.printf(TimeUtils.getTimestampIsoInstant() + " LoopingSessionReviewing is stopped.%n");
                    Thread.currentThread().interrupt();
                    return null;
                } catch (Exception ex) {
                    System.out.printf(TimeUtils.getTimestampIsoInstant() + " ERROR: LoopingSessionReviewing has stopped unexpectedly. error: %s%n", ex);
                    throw ex;
                }
            }
        };
        es.submit(innerLoopThread);
        return this;
    }

    /**
     * Determine which old sessions to kill based on what the users have
     * as their live sessions
     */
    public static List<SessionId> determineSessionsToKill(List<User> users, List<SessionId> sessions) {
        // This definitely has some poor design for performance. But it's just a sample implementation
        // This is an O(n^2) algorithm. yuck.  If there are a bunch of live sessions,
        // and a bunch of sessions to examine, then for each session we need to review the
        // list of live sessions against it.
        List<SessionId> sessionsToKill = new ArrayList<>();
        var liveSessions = users.stream().map(User::getCurrentSession).filter(Objects::nonNull).toList();
        for (SessionId s : sessions) {
            if (! isLive(s, liveSessions)) {
                sessionsToKill.add(s);
            }
        }
        return sessionsToKill;
    }

    private static boolean isLive(SessionId s, List<String> liveSessions) {
        return liveSessions.stream().anyMatch(x -> x.equals(s.getSessionCode()));
    }


    /**
     * Kills the infinite loop running inside this class.
     */
    public void stop() {
        logger.logDebug(() -> "LoopingSessionReviewing has been told to stop");
        for (int i = 0; i < 10; i++) {
            if (myThread != null) {
                logger.logDebug(() -> "LoopingSessionReviewing: Sending interrupt to thread");
                myThread.interrupt();
                return;
            } else {
                MyThread.sleep(20);
            }
        }
        throw new RuntimeException("LoopingSessionReviewing: Leaving without successfully stopping thread");
    }
}

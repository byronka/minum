package com.renomad.minum.security;

import java.util.List;
import java.util.Map;

/**
 * A security program.
 * <p>
 *     Look, it was hard to come up with a name for this class.
 *     What would you name a class whose purpose is to maintain
 *     a list of client ip addresses that have been judged as
 *     suspicious / attackers, and who will be prevented from
 *     accessing the system for varying durations, depending on
 *     the severity of the attack?
 * </p>
 * <p>
 * This class is responsible for monitoring the inmates who have
 * misbehaved in our system.  It's relatively simple - a client who
 * needs addressing will be stored in a map in this class for as
 * long as required.  After they have served their time, they
 * are released, but in certain cases (like when we deem them to
 * have no redeeming value to us) we may set their time extremely high.
 * </p>
 * <p>
 *     See also {@link UnderInvestigation}
 * </p>
 */
public interface ITheBrig {
    // Regarding the BusyWait - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @SuppressWarnings({"BusyWait"})
    ITheBrig initialize();

    /**
     * Kills the infinite loop running inside this class.
     */
    void stop();

    /**
     * Put a client in jail for some infraction, for a specified time.
     * @param clientIdentifier the client's address plus some feature identifier, like 1.2.3.4_too_freq_downloads
     * @param sentenceDuration length of stay, in milliseconds
     */
    void sendToJail(String clientIdentifier, long sentenceDuration);

    /**
     * Return true if a particular client ip address is found
     * in the list.
     */
    boolean isInJail(String clientIdentifier);

    /**
     * Get the current list of ip addresses that have been
     * judged as having carried out attacks on the system.
     */
    List<Map.Entry<String, Long>> getInmates();
}

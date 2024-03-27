package com.renomad.minum.security;

import java.util.List;

/**
 * Monitors the inmates who have misbehaved in our system.
 * <p>
 * a client who needs addressing will be stored in a map in this class for as
 * long as required.  After they have served their time, they
 * are released.
 * </p>
 * <p>
 *     See also {@link UnderInvestigation}
 * </p>
 */
public interface ITheBrig {
    // Regarding the BusyWait - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    ITheBrig initialize();

    /**
     * Kills the infinite loop running inside this class.
     */
    void stop();

    /**
     * Put a client in jail for some infraction, for a specified time.
     *
     * @param clientIdentifier the client's address plus some feature identifier, like 1.2.3.4_too_freq_downloads
     * @param sentenceDuration length of stay, in milliseconds
     * @return whether we put this client in jail
     */
    boolean sendToJail(String clientIdentifier, long sentenceDuration);

    /**
     * Return true if a particular client ip address is found
     * in the list.
     */
    boolean isInJail(String clientIdentifier);

    /**
     * Get the current list of ip addresses that have been
     * judged as having carried out attacks on the system.
     */
    List<Inmate> getInmates();
}

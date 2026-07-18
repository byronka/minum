package com.renomad.minum.security;

import java.io.IOException;
import java.util.Collection;

/**
 * Monitors the inmates who have misbehaved in our system.
 * <p>
 * a client who needs addressing will be stored in a map in this class for as
 * long as required.  After they have served their time, they
 * are released.
 * </p>
 * <p>
 *     It is expected that users of this program will add ip addresses plus
 *     a string for a complaint identifier, like "123.123.123.123_brute_forcing_login"
 *     or "123.123.123.123_failed_login_too_many_times" or some such. In
 *     particular, adding a suffix of "_vuln_seeking" will cause a client's
 *     connection to be immediately dropped at the
 *     onset - see code for {@link com.renomad.minum.web.WebFramework#dumpIfAttacker}
 * </p>
 * <p>
 *     From a technical standpoint, this program stores strings paired
 *     with timestamps, and removes those entries when hitting that time.
 * </p>
 */
public interface ITheBrig {
    // Regarding the BusyWait - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    ITheBrig initialize();

    /**
     * Kills the infinite loop running inside this class.
     */
    void stop() throws IOException;

    /**
     * Put a client in jail for some infraction, for a specified time.
     *
     * @param clientIdentifier the client's ip address plus some complaint
     *                         identifier, like 1.2.3.4_too_freq_downloads. One special
     *                         string to use is "_vuln_seeking" - if this is appended
     *                         to an ip address, then that client will have its
     *                         connection immediately dropped upon connecting. For
     *                         example, that would look like "123.123.123.123_vuln_seeking"
     *
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
    Collection<Inmate> getInmates();
}

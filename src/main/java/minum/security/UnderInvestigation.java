package minum.security;

import minum.Constants;
import minum.Context;

import java.util.List;

/**
 * Looking for bad actors in our system
 */
public class UnderInvestigation {

    private final Constants constants;

    public UnderInvestigation(Constants constants) {
        this.constants = constants;
    }

    /**
     * Check for the kinds of error messages we usually see when an attacker is trying
     * their shenanigans on us.  Returns true if we recognize anything.
     */
    public boolean isClientLookingForVulnerabilities(String exceptionMessage) {
        List<String> suspiciousErrors = constants.SUSPICIOUS_ERRORS;
        return suspiciousErrors.stream().anyMatch(exceptionMessage::contains);
    }


    /**
     * If the client is looking for paths like owa/auth/login.aspx, it means
     * they are probably some low-effort script scouring the web.  In that case
     * the client is under control by a bad actor and we can safely block them.
     */
    public boolean isLookingForSuspiciousPaths(String isolatedPath) {
        return constants.SUSPICIOUS_PATHS.stream().anyMatch(isolatedPath::equals);
    }
}

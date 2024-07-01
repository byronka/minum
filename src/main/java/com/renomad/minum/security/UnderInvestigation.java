package com.renomad.minum.security;

import com.renomad.minum.state.Constants;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Looking for bad actors in our system
 */
public final class UnderInvestigation {

    private final Constants constants;

    public UnderInvestigation(Constants constants) {
        this.constants = constants;
    }

    /**
     * Check for the kinds of error messages we usually see when an attacker is trying
     * their shenanigans on us.  Returns true if we recognize anything.
     */
    public String isClientLookingForVulnerabilities(String exceptionMessage) {
        List<String> suspiciousErrors = constants.suspiciousErrors;
        return suspiciousErrors.stream().filter(exceptionMessage::contains).collect(Collectors.joining(";"));
    }


    /**
     * If the client is looking for paths like owa/auth/login.aspx, it means
     * they are probably some low-effort script scouring the web.  In that case
     * the client is under control by a bad actor and we can safely block them.
     */
    public String isLookingForSuspiciousPaths(String isolatedPath) {
        return constants.suspiciousPaths.stream().filter(isolatedPath::equals).collect(Collectors.joining(";"));
    }
}

package com.renomad.minum.sampledomain.auth;

import java.time.ZonedDateTime;

/**
 * This data structure contains important information about
 * a particular person's authentication.  Like, are they
 * currently authenticated?
 */
public record AuthResult(Boolean isAuthenticated, ZonedDateTime creationDate, User user) {
}

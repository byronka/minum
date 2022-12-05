package atqa.auth;

import atqa.database.DatabaseDiskPersistenceSimpler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static atqa.database.SimpleIndexed.calculateNextIndex;
import static atqa.utils.Invariants.mustBeTrue;

public class AuthUtils {

    private final List<SessionId> sessionIds;
    final AtomicLong newSessionIdentifierIndex;

    /**
     * A constructor for an {@link AuthUtils}
     */
    public AuthUtils(DatabaseDiskPersistenceSimpler<SessionId> diskData) {
        sessionIds = diskData.readAndDeserialize(new SessionId("",0L));
        newSessionIdentifierIndex = new AtomicLong(calculateNextIndex(sessionIds));
    }

    /**
     * Used to extract cookies from the Cookie header
     */
    public static final Pattern sessionIdCookieRegex = Pattern.compile("sessionid=(?<sessionIdValue>\\w+)");

    /**
     * Processes the request and returns a {@link Authentication} object.
     * <br>
     * More concretely, searches the cookie header in the list of headers
     * of the request and sees if that corresponds to a valid session
     * in our database.  The object returned (the Authentication object) should
     * have all necessary information for use by domain code:
     * 1. do we know this user? (Authentication)
     * 2. Are they allowed to access this resource? (Authorization)
     * etc...
     */
    public Authentication processAuth(List<String> headers) {
        final var cookieHeaders = headers.stream()
                .map(String::toLowerCase)
                .filter(x -> x.startsWith("cookie"))
                .collect(Collectors.joining("; "));
        final var cookieMatcher = AuthUtils.sessionIdCookieRegex.matcher(cookieHeaders);
        final var listOfSessionIds = new ArrayList<String>();
        while (cookieMatcher.find()) {
            listOfSessionIds.add(cookieMatcher.group("sessionIdValue"));
        }
        mustBeTrue(listOfSessionIds.size() < 2, "there must be either zero or one session id found in the request headers.  Anything more is invalid");

        final var isExactlyOneSessionInRequest = listOfSessionIds.size() == 1;
        final var sessionFoundInDatabase = sessionIds.stream().anyMatch(x -> Objects.equals(x.sessionCode(), listOfSessionIds.get(0)));
        return new Authentication(isExactlyOneSessionInRequest && sessionFoundInDatabase);
    }
}

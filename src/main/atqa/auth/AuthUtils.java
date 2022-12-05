package atqa.auth;

import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.ILogger;

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
    private final ILogger logger;
    final AtomicLong newSessionIdentifierIndex;

    /**
     * A constructor for an {@link AuthUtils}
     */
    public AuthUtils(DatabaseDiskPersistenceSimpler<SessionId> diskData, ILogger logger) {
        sessionIds = diskData.readAndDeserialize(new SessionId("",0L));
        this.logger = logger;
        newSessionIdentifierIndex = new AtomicLong(calculateNextIndex(sessionIds));
    }

    /**
     * Used to extract cookies from the Cookie header
     */
    public static final Pattern sessionIdCookieRegex = Pattern.compile("sessionid=(?<sessionIdValue>\\w+)");

    /**
     * Processes the request and returns a {@link Authentication} object.
     * <br><br>
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
        logger.logDebug(() -> "For headers object " + headers + ". cookieHeaders were " + cookieHeaders);
        final var cookieMatcher = AuthUtils.sessionIdCookieRegex.matcher(cookieHeaders);
        final var listOfSessionIds = new ArrayList<String>();
        while (cookieMatcher.find()) {
            final var sessionIdValue = cookieMatcher.group("sessionIdValue");
            logger.logDebug(() -> "For headers object " + headers + ". Adding another sessionIdValue: " + sessionIdValue);
            listOfSessionIds.add(sessionIdValue);
        }
        mustBeTrue(listOfSessionIds.size() < 2, "there must be either zero or one session id found in the request headers.  Anything more is invalid");

        final var isExactlyOneSessionInRequest = listOfSessionIds.size() == 1;
        logger.logDebug(() -> "For headers object " + headers + ". isExactlyOneSessionInRequest is " + isExactlyOneSessionInRequest);

        final var sessionFoundInDatabase = sessionIds.stream()
                .anyMatch(x -> Objects.equals(x.sessionCode(), listOfSessionIds.get(0)));
        logger.logDebug(() -> "For headers object " + headers + ". sessionFoundInDatabase is " + sessionFoundInDatabase);

        final var isAuthenticated = isExactlyOneSessionInRequest && sessionFoundInDatabase;
        logger.logDebug(() -> "For headers object " + headers + ". isAuthenticated is " + isAuthenticated);

        return new Authentication(isAuthenticated);
    }

    /**
     * A temporary method used during construction, to add a session to the database.
     */
    public void addSession(String sessionId) {
        sessionIds.add(new SessionId(sessionId, newSessionIdentifierIndex.getAndAdd(1)));
    }
}

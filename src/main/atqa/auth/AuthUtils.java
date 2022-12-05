package atqa.auth;

import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.ILogger;
import atqa.web.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static atqa.database.SimpleIndexed.calculateNextIndex;
import static atqa.utils.Invariants.mustBeTrue;

/**
 * This class provides services for stateful authentication and
 * authorization.
 * <br><br>
 * Interestingly, it uses the underlying web framework similarly
 * to any other domain.  It doesn't really require any special
 * deeper magic.
 */
public class AuthUtils {

    private final List<SessionId> sessionIds;
    private final ILogger logger;
    final AtomicLong newSessionIdentifierIndex;

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
     * Processes the request and returns a {@link AuthResult} object.
     * <br><br>
     * More concretely, searches the cookie header in the list of headers
     * of the request and sees if that corresponds to a valid session
     * in our database.  The object returned (the {@link AuthResult} object) should
     * have all necessary information for use by domain code:
     * <ol>
     * <li>do we know this user? (Authentication)</li>
     * <li>Are they permitted to access this specific data? (Authorization)</li>
     * <li>etc...</li>
     * </ol>
     */
    public AuthResult processAuth(Request request) {
        // grab the headers from the request.
        final var headers = request.headers().rawValues();

        // get all the headers that start with "cookie", case-insensitive
        final var cookieHeaders = headers.stream()
                .map(String::toLowerCase)
                .filter(x -> x.startsWith("cookie"))
                .collect(Collectors.joining("; "));
        logger.logDebug(() -> "For headers object " + headers + ". cookieHeaders were " + cookieHeaders);

        // extract session identifiers from the cookies
        final var cookieMatcher = AuthUtils.sessionIdCookieRegex.matcher(cookieHeaders);
        final var listOfSessionIds = new ArrayList<String>();
        while (cookieMatcher.find()) {
            final var sessionIdValue = cookieMatcher.group("sessionIdValue");
            logger.logDebug(() -> "For headers object " + headers + ". Adding another sessionIdValue: " + sessionIdValue);
            listOfSessionIds.add(sessionIdValue);
        }
        mustBeTrue(listOfSessionIds.size() < 2, "there must be either zero or one session id found " +
                "in the request headers.  Anything more is invalid");

        // examine whether there is just one session identifier
        final var isExactlyOneSessionInRequest = listOfSessionIds.size() == 1;
        logger.logDebug(() -> "For headers object " + headers + ". isExactlyOneSessionInRequest is " + isExactlyOneSessionInRequest);

        // Did we find that session identifier in the database?
        final var sessionFoundInDatabase = sessionIds.stream()
                .anyMatch(x -> Objects.equals(x.sessionCode(), listOfSessionIds.get(0)));
        logger.logDebug(() -> "For headers object " + headers + ". sessionFoundInDatabase is " + sessionFoundInDatabase);

        // they are authenticated if we find their session id in the database, and
        // there was only one session id value in the cookies
        final var isAuthenticated = isExactlyOneSessionInRequest && sessionFoundInDatabase;
        logger.logDebug(() -> "For headers object " + headers + ". isAuthenticated is " + isAuthenticated);

        return new AuthResult(isAuthenticated);
    }

    /**
     * A temporary method used during construction, to add a session to the database.
     */
    public void addSession(String sessionId) {
        sessionIds.add(new SessionId(sessionId, newSessionIdentifierIndex.getAndAdd(1)));
    }
}

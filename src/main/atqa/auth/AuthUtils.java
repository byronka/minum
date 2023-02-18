package atqa.auth;

import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.ILogger;
import atqa.utils.CryptoUtils;
import atqa.utils.InvariantException;
import atqa.utils.StringUtils;
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
    private final List<User> users;
    private final ILogger logger;
    private final DatabaseDiskPersistenceSimpler<User> userDiskData;
    private final DatabaseDiskPersistenceSimpler<SessionId> sessionDiskData;
    final AtomicLong newSessionIdentifierIndex;
    final AtomicLong newUserIndex;

    public AuthUtils(DatabaseDiskPersistenceSimpler<SessionId> sessionDiskData,
                     DatabaseDiskPersistenceSimpler<User> userDiskData,
                     ILogger logger) {
        this.userDiskData = userDiskData;
        this.sessionDiskData = sessionDiskData;
        sessionIds = sessionDiskData.readAndDeserialize(SessionId.EMPTY);
        users = userDiskData.readAndDeserialize(User.EMPTY);
        this.logger = logger;
        newSessionIdentifierIndex = new AtomicLong(calculateNextIndex(sessionIds));
        newUserIndex = new AtomicLong(calculateNextIndex(users));
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

        // extract session identifiers from the cookies
        final var cookieMatcher = AuthUtils.sessionIdCookieRegex.matcher(cookieHeaders);
        final var listOfSessionIds = new ArrayList<String>();
        while (cookieMatcher.find()) {
            final var sessionIdValue = cookieMatcher.group("sessionIdValue");
            listOfSessionIds.add(sessionIdValue);
        }
        if (listOfSessionIds.size() >= 2) {
            logger.logDebug(() -> "there must be either zero or one session id found " +
                    "in the request headers.  Anything more is invalid");
            return new AuthResult(false, null, User.EMPTY);
        }

        // examine whether there is just one session identifier
        final var isExactlyOneSessionInRequest = listOfSessionIds.size() == 1;

        // if we don't find any sessions in the request, they are not authenticated.
        if (! isExactlyOneSessionInRequest) {
            return new AuthResult(false, null, User.EMPTY);
        }

        // Did we find that session identifier in the database?
        final SessionId sessionFoundInDatabase = sessionIds.stream()
                .filter(x -> Objects.equals(x.sessionCode().toLowerCase(), listOfSessionIds.get(0).toLowerCase()))
                .findFirst()
                .orElse(SessionId.EMPTY);

        // they are authenticated if we find their session id in the database
        final var isAuthenticated = sessionFoundInDatabase != SessionId.EMPTY;

        // find the user
        final List<User> authenticatedUser = users.stream().filter(x -> Objects.equals(x.currentSession(), sessionFoundInDatabase.sessionCode())).toList();

        mustBeTrue(authenticatedUser.size() == 1, "There must be exactly one user found for a current session. We found: " + authenticatedUser.size());

        return new AuthResult(isAuthenticated, sessionFoundInDatabase.creationDateTime(), authenticatedUser.get(0));
    }

    public record NewSessionResult(SessionId sessionId, User user){}

    /**
     * A temporary method used during construction, to add a session to the database.
     */
    public NewSessionResult registerNewSession(User user) {
        final var newSession = SessionId.createNewSession(newSessionIdentifierIndex.getAndAdd(1));
        sessionIds.add(newSession);
        users.remove(user);
        userDiskData.deleteOnDisk(user);
        final User updatedUser = new User(user.id(), user.username(), user.hashedPassword(), user.salt(), newSession.sessionCode());
        users.add(updatedUser);
        userDiskData.persistToDisk(updatedUser);
        return new NewSessionResult(newSession, updatedUser);
    }

    public RegisterResult registerUser(String newUsername, String newPassword) {
        if (users.stream().anyMatch(x -> x.username().equals(newUsername))) {
            return new RegisterResult(RegisterResultStatus.ALREADY_EXISTING_USER, User.EMPTY);
        }
        final var newSalt = StringUtils.generateSecureRandomString(10);
        final var hashedPassword = CryptoUtils.createHash(newPassword, newSalt);
        final var newUser = new User(newUserIndex.getAndAdd(1), newUsername, hashedPassword, newSalt, null);
        users.add(newUser);
        userDiskData.persistToDisk(newUser);
        return new RegisterResult(RegisterResultStatus.SUCCESS, newUser);
    }

    public LoginResult loginUser(String username, String password) {
        final var foundUsers = users.stream().filter(x -> x.username().equals(username)).toList();
        final var loginResult = switch (foundUsers.size()) {
            case 0 -> new LoginResult(LoginResultStatus.NO_USER_FOUND, User.EMPTY);
            case 1 -> passwordCheck(foundUsers.get(0), password);
            default ->
                    throw new InvariantException("there must be zero or one users found. Anything else indicates a bug");
        };

        if (loginResult.status().equals(LoginResultStatus.SUCCESS)) {
            final var newSessionResult = registerNewSession(loginResult.user());
            return new LoginResult(loginResult.status(), newSessionResult.user());
        }

        return loginResult;
    }

    private LoginResult passwordCheck(User user, String password) {
        final var hash = CryptoUtils.createHash(password, user.salt());
        if (user.hashedPassword().equals(hash)) {
            return new LoginResult(LoginResultStatus.SUCCESS, user);
        } else {
            return new LoginResult(LoginResultStatus.DID_NOT_MATCH_PASSWORD, User.EMPTY);
        }
    }

    public User logoutUser(User user) {
        final List<SessionId> userSession = sessionIds.stream().filter(s -> Objects.equals(s.sessionCode(), user.currentSession())).toList();
        mustBeTrue(userSession.size() == 1, "There must be exactly one session found for this active session id. Count found: " + userSession.size());
        sessionIds.remove(userSession.get(0));
        sessionDiskData.deleteOnDisk(userSession.get(0));
        users.remove(user);
        userDiskData.deleteOnDisk(user);
        final User updatedUser = new User(user.id(), user.username(), user.hashedPassword(), user.salt(), null);
        users.add(updatedUser);
        userDiskData.persistToDisk(updatedUser);
        return updatedUser;
    }
}

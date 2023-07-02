package minum.auth;

import minum.Constants;
import minum.Context;
import minum.database.DatabaseDiskPersistenceSimpler;
import minum.logging.ILogger;
import minum.utils.CryptoUtils;
import minum.utils.FileUtils;
import minum.utils.InvariantException;
import minum.utils.StringUtils;
import minum.web.Request;
import minum.web.Response;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static minum.auth.RegisterResultStatus.ALREADY_EXISTING_USER;
import static minum.database.SimpleIndexed.calculateNextIndex;
import static minum.utils.Invariants.mustBeTrue;
import static minum.web.StatusLine.StatusCode.*;

/**
 * This class provides services for stateful authentication and
 * authorization.
 * <br><br>
 * Interestingly, it uses the underlying web testing similarly
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
    private final String loginPageTemplate;
    private final String registerPageTemplate;
    private final Context context;
    private final Constants constants;
    private final StringUtils stringUtils;
    private LoopingSessionReviewing sessionLooper;

    public AuthUtils(DatabaseDiskPersistenceSimpler<SessionId> sessionDiskData,
                     DatabaseDiskPersistenceSimpler<User> userDiskData,
                     Context context) {
        this.context = context;
        this.constants = context.getConstants();
        this.stringUtils = new StringUtils(context);
        this.userDiskData = userDiskData;
        this.sessionDiskData = sessionDiskData;
        sessionIds = sessionDiskData.readAndDeserialize(SessionId.EMPTY);
        users = userDiskData.readAndDeserialize(User.EMPTY);
        this.logger = context.getLogger();

        newSessionIdentifierIndex = new AtomicLong(calculateNextIndex(sessionIds));
        newUserIndex = new AtomicLong(calculateNextIndex(users));
        loginPageTemplate = FileUtils.readTemplate("auth/login_page_template.html");
        registerPageTemplate = FileUtils.readTemplate("auth/register_page_template.html");
    }

    public static final String cookieKey = "sessionid";

    /**
     * Used to extract cookies from the Cookie header
     */
    public static final Pattern sessionIdCookieRegex = Pattern.compile(cookieKey + "=(?<sessionIdValue>\\w+)");

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
        final var headers = request.headers().getHeaderStrings();

        // get all the headers that start with "cookie", case-insensitive
        final var cookieHeaders = headers.stream()
                .map(String::toLowerCase)
                .filter(x -> x.startsWith("cookie"))
                .collect(Collectors.joining("; "));

        // extract session identifiers from the cookies
        final var cookieMatcher = AuthUtils.sessionIdCookieRegex.matcher(cookieHeaders);
        final var listOfSessionIds = new ArrayList<String>();
        for(int i = 0; cookieMatcher.find() && i < constants.MOST_COOKIES_WELL_LOOK_THROUGH; i++) {
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
                .filter(x -> Objects.equals(x.getSessionCode().toLowerCase(), listOfSessionIds.get(0).toLowerCase()))
                .findFirst()
                .orElse(SessionId.EMPTY);

        // they are authenticated if we find their session id in the database
        final var isAuthenticated = sessionFoundInDatabase != SessionId.EMPTY;

        if (! isAuthenticated) {
            return new AuthResult(false, ZonedDateTime.now(), User.EMPTY);
        }

        // find the user
        final List<User> authenticatedUser = users.stream().filter(x -> Objects.equals(x.getCurrentSession(), sessionFoundInDatabase.getSessionCode())).toList();

        mustBeTrue(authenticatedUser.size() == 1, "There must be exactly one user found for a current session. We found: " + authenticatedUser.size());

        return new AuthResult(true, sessionFoundInDatabase.getCreationDateTime(), authenticatedUser.get(0));
    }

    public void setSessionLooper(LoopingSessionReviewing sessionLooper) {
        this.sessionLooper = sessionLooper;
    }

    public List<User> getUsers() {
        return this.users.stream().toList();
    }

    public List<SessionId> getSessions() {
        return sessionIds.stream().toList();
    }

    public void deleteSession(SessionId s) {
        sessionIds.remove(s);
        sessionDiskData.deleteOnDisk(s);
    }

    public record NewSessionResult(SessionId sessionId, User user){}

    /**
     * A temporary method used during construction, to add a session to the database.
     */
    public NewSessionResult registerNewSession(User user) {
        final var newSession = SessionId.createNewSession(newSessionIdentifierIndex.getAndAdd(1), context);

        // add a new session to memory and the disk
        sessionIds.add(newSession);
        sessionDiskData.persistToDisk(newSession);

        // remove the old user details
        users.remove(user);

        // create details of the new user (the one who has a session)
        final User updatedUser = new User(user.getId(), user.getUsername(), user.getHashedPassword(), user.getSalt(), newSession.getSessionCode(), context);
        users.add(updatedUser);
        userDiskData.updateOnDisk(updatedUser);

        return new NewSessionResult(newSession, updatedUser);
    }

    public RegisterResult registerUser(String newUsername, String newPassword) {
        if (users.stream().anyMatch(x -> x.getUsername().equals(newUsername))) {
            return new RegisterResult(ALREADY_EXISTING_USER, User.EMPTY);
        }
        final var newSalt = stringUtils.generateSecureRandomString(10);
        final var hashedPassword = CryptoUtils.createPasswordHash(newPassword, newSalt);
        final var newUser = new User(newUserIndex.getAndAdd(1), newUsername, hashedPassword, newSalt, null, context);
        users.add(newUser);
        userDiskData.persistToDisk(newUser);
        return new RegisterResult(RegisterResultStatus.SUCCESS, newUser);
    }

    public LoginResult loginUser(String username, String password) {
        final LoginResult loginResult = findUser(username, password);

        if (loginResult.status().equals(LoginResultStatus.SUCCESS)) {
            final var newSessionResult = registerNewSession(loginResult.user());
            return new LoginResult(loginResult.status(), newSessionResult.user());
        }

        return loginResult;
    }

    /**
     * This is the real findUser
     */
    private LoginResult findUser(String username, String password) {
        final var foundUsers = users.stream().filter(x -> x.getUsername().equals(username)).toList();
        return switch (foundUsers.size()) {
            case 0 -> new LoginResult(LoginResultStatus.NO_USER_FOUND, User.EMPTY);
            case 1 -> passwordCheck(foundUsers.get(0), password);
            default ->
                    throw new InvariantException("there must be zero or one users found. Anything else indicates a bug");
        };
    }

    private LoginResult passwordCheck(User user, String password) {
        final var hash = CryptoUtils.createPasswordHash(password, user.getSalt());
        if (user.getHashedPassword().equals(hash)) {
            return new LoginResult(LoginResultStatus.SUCCESS, user);
        } else {
            return new LoginResult(LoginResultStatus.DID_NOT_MATCH_PASSWORD, User.EMPTY);
        }
    }

    /**
     * removes the given user's session from the list. Updates
     * the user to have a null session value.
     */
    public User logoutUser(User user) {
        final List<SessionId> userSession = sessionIds.stream().filter(s -> Objects.equals(s.getSessionCode(), user.getCurrentSession())).toList();
        mustBeTrue(userSession.size() == 1, "There must be exactly one session found for this active session id. Count found: " + userSession.size());

        sessionIds.remove(userSession.get(0));
        sessionDiskData.deleteOnDisk(userSession.get(0));

        users.remove(user);
        final User updatedUser = new User(user.getId(), user.getUsername(), user.getHashedPassword(), user.getSalt(), null, context);
        users.add(updatedUser);
        userDiskData.updateOnDisk(updatedUser);

        return updatedUser;
    }


    public Response loginUser(Request r) {
        String hostname = constants.HOST_NAME;
        if (processAuth(r).isAuthenticated()) {
            Response.redirectTo("photos");
        }

        final var username = r.body().asString("username");
        final var password = r.body().asString("password");
        final var loginResult = loginUser(username, password);

        switch (loginResult.status()) {
            case SUCCESS -> {
                return new Response(_303_SEE_OTHER, List.of(
                        "Location: index.html",
                        "Set-Cookie: %s=%s; Secure; HttpOnly; Domain=%s".formatted(cookieKey, loginResult.user().getCurrentSession(), hostname)));
            }
            default -> {
                return new Response(_401_UNAUTHORIZED,
                        List.of("Content-Type: text/html"),
                        """
                                Invalid account credentials. <a href="index.html">Index</a>
                                """);
            }
        }
    }

    public Response login(Request request) {
        AuthResult authResult = processAuth(request);
        if (authResult.isAuthenticated()) {
            return Response.redirectTo("auth");
        }
        return Response.htmlOk(loginPageTemplate);
    }


    public Response registerUser(Request r) {
        final var authResult = processAuth(r);
        if (authResult.isAuthenticated()) {
            return new Response(_303_SEE_OTHER, List.of("Location: index"));
        }

        final var username = r.body().asString("username");
        final var password = r.body().asString("password");
        final var registrationResult = registerUser(username, password);

        if (registrationResult.status() == ALREADY_EXISTING_USER) {
            return new Response(_401_UNAUTHORIZED, "<p>This user is already registered</p><p><a href=\"index.html\">Index</a></p>", List.of("content-type: text/html"));
        }
        return new Response(_303_SEE_OTHER, List.of("Location: login"));

    }

    public Response register(Request request) {
        AuthResult authResult = processAuth(request);
        if (authResult.isAuthenticated()) {
            Response.redirectTo("auth");
        }
        return Response.htmlOk(registerPageTemplate);
    }

    public Response logout(Request request) {
        final var authResult = processAuth(request);
        if (authResult.isAuthenticated()) {
            logoutUser(authResult.user());
        }

        return Response.redirectTo("index.html");
    }

    public Response authPage(Request request) {
        String response;
        if (processAuth(request).isAuthenticated()) {
            response = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Authentication | example1</title>
                </head>
                <body>
                    <h3>Authenticate</h3>
                    <p>
                        <p><a href="logout">Logout</a></p>
                        <a href="index.html">Index</a>
                    </p>
                </body>
                </html>
                """.stripIndent();
        } else {
            response = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Authentication | example1</title>
                </head>
                <body>
                    <h3>Authenticate</h3>
                    <p>
                        <a href="register">Register</a>
                        <a href="login">Login</a>
                        <a href="index.html">Index</a>
                    </p>
                </body>
                </html>
                """.stripIndent();
        }
        return Response.htmlOk(response);
    }
}

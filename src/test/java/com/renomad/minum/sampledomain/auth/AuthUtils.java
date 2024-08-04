package com.renomad.minum.sampledomain.auth;

import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.CryptoUtils;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.InvariantException;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;
import com.renomad.minum.web.Response;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static com.renomad.minum.utils.Invariants.mustBeTrue;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

/**
 * This class provides services for stateful authentication and
 * authorization.
 * <br><br>
 * Interestingly, it uses the underlying web testing similarly
 * to any other domain.  It doesn't really require any special
 * deeper magic.
 */
public class AuthUtils {

    private final ILogger logger;
    private final Db<User> userDiskData;
    private final Db<SessionId> sessionDiskData;

    private final String loginPageTemplate;
    private final String registerPageTemplate;
    private final Constants constants;
    private final SessionId emptySessionId;
    private static final int MOST_COOKIES_WE_WILL_LOOK_THROUGH = 5;

    public AuthUtils(Db<SessionId> sessionDiskData,
                     Db<User> userDiskData,
                     Context context) {
        this.constants = context.getConstants();
        this.userDiskData = userDiskData;
        this.sessionDiskData = sessionDiskData;
        emptySessionId = SessionId.EMPTY;
        this.logger = context.getLogger();
        FileUtils fileUtils = new FileUtils(logger, constants);

        loginPageTemplate = fileUtils.readTextFile("src/test/webapp/templates/auth/login_page_template.html");
        registerPageTemplate = fileUtils.readTextFile("src/test/webapp/templates/auth/register_page_template.html");
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
    public AuthResult processAuth(IRequest request) {
        // grab the headers from the request.
        List<String> cookieValues = request.getHeaders().valueByKey("cookie");
        final var cookieHeaders = String.join(";", cookieValues == null ? List.of("") : cookieValues);

        // extract session identifiers from the cookies
        final var cookieMatcher = AuthUtils.sessionIdCookieRegex.matcher(cookieHeaders);
        final var listOfSessionIds = new ArrayList<String>();
        for(int i = 0; cookieMatcher.find() && i < MOST_COOKIES_WE_WILL_LOOK_THROUGH; i++) {
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
        final SessionId sessionFoundInDatabase = sessionDiskData.values().stream()
                .filter(x -> Objects.equals(x.getSessionCode().toLowerCase(Locale.ROOT), listOfSessionIds.getFirst().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(emptySessionId);

        // they are authenticated if we find their session id in the database
        final var isAuthenticated = !Objects.equals(sessionFoundInDatabase, emptySessionId);

        if (! isAuthenticated) {
            return new AuthResult(false, ZonedDateTime.now(ZoneId.of("UTC")), User.EMPTY);
        }

        // find the user
        final List<User> authenticatedUser = userDiskData.values().stream().filter(x -> Objects.equals(x.getCurrentSession(), sessionFoundInDatabase.getSessionCode())).toList();

        mustBeTrue(authenticatedUser.size() == 1, "There must be exactly one user found for a current session. We found: " + authenticatedUser.size());

        return new AuthResult(true, sessionFoundInDatabase.getCreationDateTime(), authenticatedUser.getFirst());
    }

    public List<User> getUsers() {
        return this.userDiskData.values().stream().toList();
    }

    public List<SessionId> getSessions() {
        return sessionDiskData.values().stream().toList();
    }

    public void deleteSession(SessionId s) {
        sessionDiskData.delete(s);
    }

    public record NewSessionResult(SessionId sessionId, User user){}

    /**
     * A temporary method used during construction, to add a session to the database.
     */
    public NewSessionResult registerNewSession(User user) {
        final var newSession = SessionId.createNewSession(0);

        // add a new session to memory and the disk
        sessionDiskData.write(newSession);

        // create details of the new user (the one who has a session)
        final User updatedUser = new User(user.getId(), user.getUsername(), user.getHashedPassword(), user.getSalt(), newSession.getSessionCode());
        userDiskData.write(updatedUser);

        return new NewSessionResult(newSession, updatedUser);
    }

    public RegisterResult registerUser(String newUsername, String newPassword) {
        if (userDiskData.values().stream().anyMatch(x -> x.getUsername().equals(newUsername))) {
            return new RegisterResult(RegisterResultStatus.ALREADY_EXISTING_USER, User.EMPTY);
        }
        final var newSalt = StringUtils.generateSecureRandomString(10);
        final var hashedPassword = CryptoUtils.createPasswordHash(newPassword, newSalt);
        final var newUser = new User(0, newUsername, hashedPassword, newSalt, null);
        userDiskData.write(newUser);
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
        final var foundUsers = userDiskData.values().stream().filter(x -> x.getUsername().equals(username)).toList();
        return switch (foundUsers.size()) {
            case 0 -> new LoginResult(LoginResultStatus.NO_USER_FOUND, User.EMPTY);
            case 1 -> passwordCheck(foundUsers.getFirst(), password);
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
        final List<SessionId> userSession = sessionDiskData.values().stream().filter(s -> Objects.equals(s.getSessionCode(), user.getCurrentSession())).toList();
        mustBeTrue(userSession.size() == 1, "There must be exactly one session found for this active session id. Count found: " + userSession.size());

        sessionDiskData.delete(userSession.getFirst());

        final User updatedUser = new User(user.getId(), user.getUsername(), user.getHashedPassword(), user.getSalt(), null);
        userDiskData.write(updatedUser);

        return updatedUser;
    }


    public IResponse loginUser(IRequest r) {
        String hostname = constants.hostName;
        if (processAuth(r).isAuthenticated()) {
            Response.redirectTo("photos");
        }

        final var username = r.getBody().asString("username");
        final var password = r.getBody().asString("password");
        final var loginResult = loginUser(username, password);

        switch (loginResult.status()) {
            case SUCCESS -> {
                return Response.buildLeanResponse(CODE_303_SEE_OTHER, Map.of(
                        "Location","index.html",
                        "Set-Cookie","%s=%s; Secure; HttpOnly; Domain=%s".formatted(cookieKey, loginResult.user().getCurrentSession(), hostname)));
            }
            default -> {
                return Response.buildResponse(CODE_401_UNAUTHORIZED,
                        Map.of("Content-Type","text/html"),
                        """
                        Invalid account credentials. <a href="index.html">Index</a>
                        """
                        );
            }
        }
    }

    public IResponse login(IRequest request) {
        AuthResult authResult = processAuth(request);
        if (authResult.isAuthenticated()) {
            return Response.redirectTo("auth");
        }
        return Response.htmlOk(loginPageTemplate);
    }


    public IResponse registerUser(IRequest r) {
        final var authResult = processAuth(r);
        if (authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_303_SEE_OTHER, Map.of("Location","index"));
        }

        final var username = r.getBody().asString("username");
        final var password = r.getBody().asString("password");
        final var registrationResult = registerUser(username, password);

        if (registrationResult.status() == RegisterResultStatus.ALREADY_EXISTING_USER) {
            return Response.buildResponse(CODE_401_UNAUTHORIZED, Map.of("content-type", "text/html"), "<p>This user is already registered</p><p><a href=\"index.html\">Index</a></p>");
        }
        return Response.buildLeanResponse(CODE_303_SEE_OTHER, Map.of("Location","login"));

    }

    public IResponse register(IRequest request) {
        AuthResult authResult = processAuth(request);
        if (authResult.isAuthenticated()) {
            Response.redirectTo("auth");
        }
        return Response.htmlOk(registerPageTemplate);
    }

    public IResponse logout(IRequest request) {
        final var authResult = processAuth(request);
        if (authResult.isAuthenticated()) {
            logoutUser(authResult.user());
        }

        return Response.redirectTo("index.html");
    }

    public IResponse authPage(IRequest request) {
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

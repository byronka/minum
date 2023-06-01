package atqa.auth;

import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.testing.TestLogger;
import atqa.utils.FileUtils;
import atqa.web.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static atqa.testing.TestFramework.*;
import static atqa.web.StatusLine.StatusCode._200_OK;
import static atqa.web.StatusLine.StatusCode._401_UNAUTHORIZED;


public class AuthenticationTests {
    private final TestLogger logger;

    public AuthenticationTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) throws IOException {

        /*
         * Session management is the term used for making sure we know who we are talking with.
         * See https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
         *
         * In a previous iteration of this type of project, the application would
         * track the currently authenticated users.  "Current" being
         * dependent on the duration of a user remaining authenticated.
         *
         * There are various ways to handle the duration.  Simplest is: once authenticated,
         * they stay authenticated, until they actively choose to logout.
         *
         * While the simplest approach may be insecure, it's a good place to start.
         *
         * Even though security is a necessary and intrinsic element of the web,
         * one essential aspect - database persistence of session information -
         * makes it better to split out into its own domain.  That is to say, we
         * don't want to code it too deeply into the inner guts of our web testing.
         * And we don't need to.  The important code can be provided as helper methods.
         *
         * Admittedly, there will be a bit of overlap - it's not possible to be
         * perfectly decoupled.  For example, the auth package will need use of
         * HTTP status codes that live in atqa.web.StatusLine.StatusCode.
         *
         */
        logger.test("playing with session management"); {
            final var sessionsDdps = new DatabaseDiskPersistenceSimpler<SessionId>(Path.of("out/simple_db/sessions"), es, logger);
            final var usersDdps = new DatabaseDiskPersistenceSimpler<User>(Path.of("out/simple_db/users"), es, logger);
            var wf = new WebFramework(es, logger, Path.of("out/simple_db"));
            final var au = new AuthUtils(sessionsDdps, usersDdps, wf);

            /*
            create a pretend web handler just for this test that requires authentication

            Note how there is a branch in the code based on whether the request is
            authenticated or not.  It is basically that easy.
             */
            final var sampleAuthenticatedWebHandler = new Function<Request, Response>() {
                @Override
                public Response apply(Request request) {
                    final AuthResult auth = au.processAuth(request);
                    if (auth.isAuthenticated()) {
                        return new Response(_200_OK, List.of("All is well"));
                    } else {
                        return new Response(_401_UNAUTHORIZED, List.of("Your credentials are unrecognized"));
                    }
                }
            };

            // register a user
            final var newUser = au.registerUser("username", "password").newUser();

            // register a session
            final var newSessionResult = au.registerNewSession(newUser);

            // build an incoming request that has appropriate authentication information
            final Request authenticatedRequest = buildRequest(List.of(newSessionResult.sessionId().sessionCode()));

            // run the web handler on the authenticated request, get a response
            final var response = sampleAuthenticatedWebHandler.apply(authenticatedRequest);

            assertEquals(
                    response.extraHeaders(),
                    List.of("All is well"),
                    "The web handler should treat the request as authenticated");

            logger.test("make sure we can tell when a session id was created");
            final var authResult = au.processAuth(authenticatedRequest);
            assertTrue(Duration.between(authResult.creationDate(), ZonedDateTime.now()).toMillis() < 100,
                    "The date and time of the creation of this session id should be within 100 milliseconds of now.");

            logger.test("make sure it throws an exception if we have a request with two session identifiers.");

            final var multipleSessionIdsInRequest = buildRequest(List.of("abc","def"));
            final var authResult2 = au.processAuth(multipleSessionIdsInRequest);

            // if we send two sessionid values in the cookies, the system will not know
            // which is the valid one, and will just fail authentication.
            assertEquals(authResult2, new AuthResult(false, null, User.EMPTY));

            // Incorporate the concept of a user to the authentication process

            /*
             * In our paradigm, a user provides his account credentials, the system checks these
             * against its records, and if they match, he's allowed in.
             *
             * This test covers the bases for the happy path flow - which is:
             * 1. registering a user
             * 2. successful login
             */
            logger.test("a user with valid credentials should be allowed in");

            /*
            this piece is interesting.  Notice how the password entry is plain text? It means
            that if someone were able to print out memory on the computer, they could see
            the password for this user.  Even if we tried something sneaky like hashing
            their password on the browser (in JavaScript) and sending that to the back-end,
            the fact remains that whatever that text ends up being, an evesdropper could see it
            and use it to auth with the back-end.

            Another issue is that users are infamous about choosing bad passwords.  Maybe we
            generate them a good one and show it just once.  That's what we did in R3z.
            */
            final var newPassword = "password_123";
            final var newUsername = "alice";
            final RegisterResult registerResult = au.registerUser(newUsername, newPassword);
            assertEquals(registerResult.status(), RegisterResultStatus.SUCCESS);

            final LoginResult loginResult = au.loginUser(newUsername, newPassword);
            assertEquals(loginResult.status(), LoginResultStatus.SUCCESS);

            /*
             * When the user wishes not to be authenticated any more, they will choose to
             * log out.  From their point of view, they are universally unauthenticated.
             * From the system's point of view, their user object has an empty string for
             * its session id, and the session id has been removed from the list of session ids.
             */
            logger.test("It should be possible to log-out a user");

            final var updatedUser = au.logoutUser(loginResult.user());

            assertTrue(updatedUser.currentSession() == null);

            FileUtils.deleteDirectoryRecursivelyIfExists(Path.of("out/simple_db"), logger);
            }

            logger.test("Ensure that our code can clear out old sessions"); {
                // our users hold onto these sessions, abc and jkl.
                List<User> users = List.of(
                        new User(1L, "", "", "", "abc"),
                        new User(2L, "", "", "", "jkl")
                );

                var default_zdt = ZonedDateTime.of(2022, Month.JANUARY.getValue(), 4, 9, 25, 0, 0, ZoneId.of("UTC"));
                // in the current list of sessions, there are some stale values, namely def and ghi
                List<SessionId> sessions = List.of(
                        new SessionId(1,"abc", default_zdt),
                        new SessionId(2,"def", default_zdt),
                        new SessionId(3,"ghi", default_zdt),
                        new SessionId(4,"jkl", default_zdt)
                );

                // we expect, running our program, for it to determine def and ghi are to be killed
                List<SessionId> expected = List.of(
                        new SessionId(2,"def", default_zdt),
                        new SessionId(3,"ghi", default_zdt)
                );

                List<SessionId> actual = LoopingSessionReviewing.determineSessionsToKill(users, sessions);

                assertEquals(expected, actual);
            }


    }

    private static Request buildRequest(List<String> sessionIds) {
        return new Request(
                new Headers(sessionIds.stream().map(x -> "Cookie: sessionid=" + x).toList()),
                null,
                Body.EMPTY,
                "the remote requester");
    }


}

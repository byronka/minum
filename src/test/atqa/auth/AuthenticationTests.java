package atqa.auth;

import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.TestLogger;
import atqa.utils.InvariantException;
import atqa.web.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static atqa.framework.TestFramework.*;
import static atqa.web.StatusLine.StatusCode._200_OK;
import static atqa.web.StatusLine.StatusCode._401_UNAUTHORIZED;


public class AuthenticationTests {
    private final TestLogger logger;

    public AuthenticationTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) {

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
         * don't want to code it too deeply into the inner guts of our web framework.
         * And we don't need to.  The important code can be provided as helper methods.
         *
         * Admittedly, there will be a bit of overlap - it's not possible to be
         * perfectly decoupled.  For example, the auth package will need use of
         * HTTP status codes that live in atqa.web.StatusLine.StatusCode.
         *
         */
        logger.test("playing with session management"); {
            final var authUtilsDdps = new DatabaseDiskPersistenceSimpler<SessionId>("out/simple_db/sessions", es, logger);
            final var au = new AuthUtils(authUtilsDdps, logger);

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

            // register a session
            final var sessionId = au.registerNewSession();

            // build an incoming request that has appropriate authentication information
            final Request authenticatedRequest = buildRequest(List.of(sessionId.sessionCode()));

            // run the web handler on the authenticated request, get a response
            final var response = sampleAuthenticatedWebHandler.apply(authenticatedRequest);

            assertEquals(
                    response.extraHeaders(),
                    List.of("All is well"),
                    "The web handler should treat the request as authenticated");

            // make sure we can tell when a session id was created
            final var authResult = au.processAuth(authenticatedRequest);
            assertTrue(Duration.between(authResult.creationDate(), ZonedDateTime.now()).toMillis() < 100,
                    "The date and time of the creation of this session id should be within 100 milliseconds of now.");

        }

        logger.test("make sure it throws an exception if we have a request with two session identifiers."); {
            final var authUtilsDdps = new DatabaseDiskPersistenceSimpler<SessionId>("out/simple_db/sessions", es, logger);
            final var au = new AuthUtils(authUtilsDdps, logger);
            final var multipleSessionIdsInRequest = buildRequest(List.of("abc","def"));
            final var ex = assertThrows(InvariantException.class, () -> au.processAuth(multipleSessionIdsInRequest));
            assertEquals(ex.getMessage(), "there must be either zero or one session id found in the request headers.  Anything more is invalid");
        }

    }

    private static Request buildRequest(List<String> sessionIds) {
        return new Request(
                new Headers(0, sessionIds.stream().map(x -> "Cookie: sessionid=" + x).toList()),
                null,
                "");
    }


}

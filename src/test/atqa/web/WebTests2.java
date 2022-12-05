package atqa.web;

import atqa.auth.AuthUtils;
import atqa.auth.Authentication;
import atqa.auth.SessionId;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.TestLogger;
import atqa.utils.InvariantException;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static atqa.framework.TestFramework.assertEquals;
import static atqa.framework.TestFramework.assertThrows;
import static atqa.web.StatusLine.StatusCode._200_OK;
import static atqa.web.StatusLine.StatusCode._401_UNAUTHORIZED;


public class WebTests2 {
    private final TestLogger logger;

    public WebTests2(TestLogger logger) {
        this.logger = logger;
    }

    /*$      /$$           /$$               /$$                           /$$
    | $$  /$ | $$          | $$              | $$                          | $$
    | $$ /$$$| $$  /$$$$$$ | $$$$$$$        /$$$$$$    /$$$$$$   /$$$$$$$ /$$$$$$   /$$$$$$$
    | $$/$$ $$ $$ /$$__  $$| $$__  $$      |_  $$_/   /$$__  $$ /$$_____/|_  $$_/  /$$_____/
    | $$$$_  $$$$| $$$$$$$$| $$  \ $$        | $$    | $$$$$$$$|  $$$$$$   | $$   |  $$$$$$
    | $$$/ \  $$$| $$_____/| $$  | $$        | $$ /$$| $$_____/ \____  $$  | $$ /$$\____  $$
    | $$/   \  $$|  $$$$$$$| $$$$$$$/        |  $$$$/|  $$$$$$$ /$$$$$$$/  |  $$$$//$$$$$$$/
    |__/     \__/ \_______/|_______/          \___/   \_______/|_______/    \___/ |______*/
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
            final var au = new AuthUtils(authUtilsDdps);

            final var foo = new Function<Request, Response>() {
                @Override
                public Response apply(Request request) {
                    final Authentication auth = au.processAuth(request.headers().rawValues());
                    if (auth.isAuthenticated()) {
                        return new Response(_200_OK, List.of("All is well"));
                    } else {
                        return new Response(_401_UNAUTHORIZED, List.of("Your credentials are unrecognized"));
                    }
                }
            };

            final var response = foo.apply(buildAuthenticatedRequest());
            assertEquals(response.extraHeaders(), List.of("All is well"));

            // make sure it throws an exception if we have a request with two session identifiers.
            final var ex = assertThrows(InvariantException.class, () -> au.processAuth(List.of("cookie: sessionId=abc", "cookie: sessionId=def")));
            assertEquals(ex.getMessage(), "there must be either zero or one session id found in the request headers.  Anything more is invalid");
        }
    }


    private Request buildAuthenticatedRequest() {
        return new Request(
                new Headers(0, List.of("Cookie: sessionid=abc123")),
                new StartLine(
                        StartLine.Verb.GET,
                        new StartLine.PathDetails("foo", "", Collections.emptyMap()),
                        Web.HttpVersion.ONE_DOT_ONE,
                        "Unimportant - raw value"
                ),
                "");
    }
}

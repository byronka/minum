package atqa.sampledomain;

import atqa.auth.AuthUtils;
import atqa.auth.SessionId;
import atqa.auth.User;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.TestLogger;
import atqa.web.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static atqa.framework.TestFramework.assertEquals;
import static atqa.framework.TestFramework.assertTrue;
import static atqa.utils.StringUtils.bytesToString;

public class SampleDomainTests {

    private final TestLogger logger;

    public SampleDomainTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) throws IOException {
        // setup
        final var authUtils = setupAuthUtils(es, logger);
        final var sd = setupSampleDomain(es, logger, authUtils);
        final var sessionId = authUtils.registerNewSession();

        logger.test("basic happy-path for auth - view form"); {
            final var request = buildRequest(List.of("Cookie: sessionId="+sessionId.sessionCode()));
            final var response = sd.formEntry(request);
            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
        }

        logger.test("should receive a 401 if no cookie - view form"); {
            final var request = buildRequest(List.of());
            final var response = sd.formEntry(request);
            assertEquals(response.statusCode(), StatusLine.StatusCode._401_UNAUTHORIZED);
        }

        logger.test("happy-path for auth - form entry"); {
            final var request = buildRequest(List.of("Cookie: sessionId="+sessionId.sessionCode()));
            final var response = sd.testform(request);
            assertEquals(response.statusCode(), StatusLine.StatusCode._303_SEE_OTHER);
        }

        logger.test("should receive a 401 if no cookie - form entry"); {
            final var request = buildRequest(List.of());
            final var response = sd.testform(request);
            assertEquals(response.statusCode(), StatusLine.StatusCode._401_UNAUTHORIZED);
        }

        logger.test("should be able to register a new user"); {
            final var request = buildRequest("username=abc&password=123");
            final var response = sd.registerUser(request);
            assertEquals(response.statusCode(), StatusLine.StatusCode._303_SEE_OTHER);
        }

        logger.test("should prevent registering a user twice"); {
            final var request = buildRequest("username=abc&password=123");
            final var response = sd.registerUser(request);
            assertEquals(bytesToString(response.body()), "This user is already registered");
        }

        logger.test("should be able to login a new user"); {
            final var request = buildRequest("username=abc&password=123");
            final var response = sd.loginUser(request);
            assertTrue(response.extraHeaders().stream().anyMatch(x -> x.toLowerCase().contains("set-cookie")), "the headers, (" + String.join(";", response.extraHeaders()) + ") must contain a set-cookie header");
            assertEquals(response.statusCode(), StatusLine.StatusCode._303_SEE_OTHER);
        }

        logger.test("should stop a bad password"); {
            final var request = buildRequest("username=abc&password=bad");
            final var response = sd.loginUser(request);
            assertEquals(response.statusCode(), StatusLine.StatusCode._401_UNAUTHORIZED);
        }
    }

    private static Request buildRequest(List<String> headers) {
        return new Request(new Headers(0, ContentType.NONE, headers), null, "", Map.of());
    }

    private static Request buildRequest(String body) {
        return new Request(new Headers(0, ContentType.NONE, Collections.emptyList()), null, body, WebFramework.parseUrlEncodedForm(body));
    }

    private static AuthUtils setupAuthUtils(ExecutorService es, TestLogger logger) {
        final var sessionDdps = new DatabaseDiskPersistenceSimpler<SessionId>("out/simple_db/sessions", es, logger);
        final var userDdps = new DatabaseDiskPersistenceSimpler<User>("out/simple_db/users", es, logger);
        return new AuthUtils(sessionDdps, userDdps, logger);
    }

    private static SampleDomain setupSampleDomain(ExecutorService es, TestLogger logger, AuthUtils auth) {
        final var sampleDomainDdps = new DatabaseDiskPersistenceSimpler<PersonName>("out/simple_db/names", es, logger);
        return new SampleDomain(sampleDomainDdps, auth);
    }
}

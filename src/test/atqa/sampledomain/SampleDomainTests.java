package atqa.sampledomain;

import atqa.auth.AuthUtils;
import atqa.auth.SessionId;
import atqa.auth.User;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.testing.TestLogger;
import atqa.utils.StringUtils;
import atqa.web.*;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static atqa.testing.TestFramework.assertEquals;
import static atqa.testing.TestFramework.assertTrue;


public class SampleDomainTests {

    private final TestLogger logger;

    public SampleDomainTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) {
        // setup
        final var authUtils = setupAuthUtils(es, logger);
        final var sd = setupSampleDomain(es, logger, authUtils);
        final var registerResult = authUtils.registerUser("a", "b");
        final var newSessionResult = authUtils.registerNewSession(registerResult.newUser());

        logger.test("basic happy-path for auth - view form"); {
            final var request = buildRequest(List.of("Cookie: sessionId="+newSessionResult.sessionId().sessionCode()));
            final var response = sd.formEntry(request);
            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
        }

        logger.test("should receive a 401 if no cookie - view form"); {
            final var request = buildRequest(List.of());
            final var response = sd.formEntry(request);
            assertEquals(response.statusCode(), StatusLine.StatusCode._401_UNAUTHORIZED);
        }

        logger.test("happy-path for auth - form entry"); {
            final var request = buildRequest(List.of("Cookie: sessionId="+newSessionResult.sessionId().sessionCode()));
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
            final var response = authUtils.registerUser(request);
            assertEquals(response.statusCode(), StatusLine.StatusCode._303_SEE_OTHER);
        }

        logger.test("should prevent registering a user twice"); {
            final var request = buildRequest("username=abc&password=123");
            final var response = authUtils.registerUser(request);
            assertEquals(StringUtils.byteArrayToString(response.body()), "This user is already registered");
        }

        logger.test("should be able to login a new user"); {
            final var request = buildRequest("username=abc&password=123");
            final var response = authUtils.loginUser(request);
            assertTrue(response.extraHeaders().stream().anyMatch(x -> x.toLowerCase().contains("set-cookie")), "the headers, (" + String.join(";", response.extraHeaders()) + ") must contain a set-cookie header");
            assertEquals(response.statusCode(), StatusLine.StatusCode._303_SEE_OTHER);
        }

        logger.test("should stop a bad password"); {
            final var request = buildRequest("username=abc&password=bad");
            final var response = authUtils.loginUser(request);
            assertEquals(response.statusCode(), StatusLine.StatusCode._401_UNAUTHORIZED);
        }

        logger.test("should properly deserialize"); {
            final var pn = new PersonName(1L, "abc");
            final var result = pn.deserialize(pn.serialize());
            assertEquals(pn, result);
        }
    }

    private static Request buildRequest(List<String> headers) {
        return new Request(new Headers(headers), null, Body.EMPTY, "1.2.3.4");
    }

    private static Request buildRequest(String body) {
        return new Request(new Headers(Collections.emptyList()), null, BodyProcessor.parseUrlEncodedForm(body), "1.2.3.4");
    }

    private static AuthUtils setupAuthUtils(ExecutorService es, TestLogger logger) {
        var wf = new WebFramework(es, logger, Path.of("out/simple_db"));
        final var sessionDdps = new DatabaseDiskPersistenceSimpler<SessionId>(Path.of("out/simple_db/sessions"), es, logger);
        final var userDdps = new DatabaseDiskPersistenceSimpler<User>(Path.of("out/simple_db/users"), es, logger);
        return new AuthUtils(sessionDdps, userDdps, wf);
    }

    private static SampleDomain setupSampleDomain(ExecutorService es, TestLogger logger, AuthUtils auth) {
        final var sampleDomainDdps = new DatabaseDiskPersistenceSimpler<PersonName>(Path.of("out/simple_db/names"), es, logger);
        return new SampleDomain(sampleDomainDdps, auth);
    }
}

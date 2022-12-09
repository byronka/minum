package atqa.sampledomain;

import atqa.auth.AuthUtils;
import atqa.auth.SessionId;
import atqa.auth.User;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.TestLogger;
import atqa.web.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static atqa.framework.TestFramework.assertEquals;

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
            final var firstRequest = buildRequest(List.of("Cookie: sessionId="+sessionId.sessionCode()));
            final var response = sd.formEntry(firstRequest);
            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
        }

        logger.test("should receive a 401 if no cookie - view form"); {
            final var firstRequest = buildRequest(List.of());
            final var response = sd.formEntry(firstRequest);
            assertEquals(response.statusCode(), StatusLine.StatusCode._401_UNAUTHORIZED);
        }

        logger.test("happy-path for auth - form entry"); {
            final var firstRequest = buildRequest(List.of("Cookie: sessionId="+sessionId.sessionCode()));
            final var response = sd.testform(firstRequest);
            assertEquals(response.statusCode(), StatusLine.StatusCode._303_SEE_OTHER);
        }

        logger.test("should receive a 401 if no cookie - form entry"); {
            final var firstRequest = buildRequest(List.of());
            final var response = sd.testform(firstRequest);
            assertEquals(response.statusCode(), StatusLine.StatusCode._401_UNAUTHORIZED);
        }
    }

    private static Request buildRequest(List<String> headers) {
        return new Request(new Headers(0, headers), null, "");
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

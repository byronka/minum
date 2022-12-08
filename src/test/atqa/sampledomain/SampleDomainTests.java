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
        logger.test("basic happy-path for authentication in the sample domain"); {
            final var sessionDdps = new DatabaseDiskPersistenceSimpler<SessionId>("out/simple_db/sessions", es, logger);
            final var userDdps = new DatabaseDiskPersistenceSimpler<User>("out/simple_db/users", es, logger);
            final var auth = new AuthUtils(sessionDdps, userDdps, logger);
            final var sessionId = auth.registerNewSession();

            final var sampleDomainDdps = new DatabaseDiskPersistenceSimpler<PersonName>("out/simple_db/names", es, logger);
            final var sd = new SampleDomain(sampleDomainDdps, auth);
            final var firstRequest = new Request(new Headers(0, List.of("Cookie: sessionId="+sessionId.sessionCode())), null, "");
            final var response = sd.testform(firstRequest);
            assertEquals(response.statusCode(), StatusLine.StatusCode._303_SEE_OTHER);
        }
    }
}

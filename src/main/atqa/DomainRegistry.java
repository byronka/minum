package atqa;

import atqa.auth.SessionId;
import atqa.auth.User;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.ILogger;
import atqa.sampledomain.PersonName;
import atqa.sampledomain.SampleDomain;
import atqa.web.StartLine;
import atqa.web.WebFramework;

import java.util.concurrent.ExecutorService;

/**
 * This class is where all domains are registered.
 */
public class DomainRegistry {

    public static void registerDomains(WebFramework wf, ExecutorService es, ILogger logger) {
        registerSampleDomain(wf, es, logger);
        registerAuthenticationDomain(wf, es, logger);
    }

    private static void registerAuthenticationDomain(WebFramework wf, ExecutorService es, ILogger logger) {
        final var sessionDdps = new DatabaseDiskPersistenceSimpler<SessionId>("out/simple_db/sessions", es, logger);
        final var userDdps = new DatabaseDiskPersistenceSimpler<User>("out/simple_db/users", es, logger);
    }

    private static void registerSampleDomain(WebFramework wf, ExecutorService es, ILogger logger) {
        final var sampleDomainDdps = new DatabaseDiskPersistenceSimpler<PersonName>("out/simple_db/names", es, logger);
        final var sd = new SampleDomain(sampleDomainDdps);
        wf.registerPath(StartLine.Verb.GET, "", WebFramework.redirectTo("index.html"));
        wf.registerPath(StartLine.Verb.GET, "formentry", sd::formEntry);
        wf.registerPath(StartLine.Verb.POST, "testform", sd::testform);
    }
}

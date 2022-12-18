package atqa;

import atqa.auth.AuthUtils;
import atqa.auth.SessionId;
import atqa.auth.User;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.sampledomain.PersonName;
import atqa.sampledomain.SampleDomain;
import atqa.web.StartLine;
import atqa.web.WebFramework;

/**
 * This class is where all code gets registered to work
 * with our web framework.
 * <br><br>
 * example:
 * <pre>{@code
 *     wf.registerPath(StartLine.Verb.GET, "formentry", sd::formEntry);
 * }</pre>
 */
public class TheRegister {

    public static void registerDomains(WebFramework wf) {
        registerSampleDomain(wf);
    }

    private static void registerSampleDomain(WebFramework wf) {
        final var sessionDdps = new DatabaseDiskPersistenceSimpler<SessionId>("out/simple_db/sessions", wf.executorService, wf.logger);
        final var userDdps = new DatabaseDiskPersistenceSimpler<User>("out/simple_db/users", wf.executorService, wf.logger);
        final var auth = new AuthUtils(sessionDdps, userDdps, wf.logger);

        final var sampleDomainDdps = new DatabaseDiskPersistenceSimpler<PersonName>("out/simple_db/names", wf.executorService, wf.logger);
        final var sd = new SampleDomain(sampleDomainDdps, auth);
        wf.registerPath(StartLine.Verb.GET, "", WebFramework.redirectTo("index.html"));
        wf.registerPath(StartLine.Verb.GET, "formentry", sd::formEntry);
        wf.registerPath(StartLine.Verb.POST, "testform", sd::testform);
    }
}

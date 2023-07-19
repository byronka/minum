package minum.sampledomain;

import minum.auth.AuthUtils;
import minum.database.AlternateDatabaseDiskPersistenceSimpler;
import minum.sampledomain.PersonName;
import minum.templating.TemplateProcessor;
import minum.utils.FileUtils;
import minum.utils.StringUtils;
import minum.web.Request;
import minum.web.Response;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static minum.web.StatusLine.StatusCode.*;


public class SampleDomain {

    private final AlternateDatabaseDiskPersistenceSimpler<PersonName> ddps;
    private final AuthUtils auth;
    private final TemplateProcessor nameEntryTemplate;
    private final String authHomepage;
    private final String unauthHomepage;

    public SampleDomain(AlternateDatabaseDiskPersistenceSimpler<PersonName> diskData, AuthUtils auth) {
        this.ddps = diskData;
        this.auth = auth;
        nameEntryTemplate = TemplateProcessor.buildProcessor(FileUtils.readTemplate("sampledomain/name_entry.html"));
        authHomepage = FileUtils.readTemplate("sampledomain/auth_homepage.html");
        unauthHomepage = FileUtils.readTemplate("sampledomain/unauth_homepage.html");
    }

    public Response formEntry(Request r) {
        final var authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return new Response(_401_UNAUTHORIZED);
        }
        final String names = ddps
                .stream().sorted(Comparator.comparingLong(PersonName::getIndex))
                .map(x -> "<li>" + StringUtils.safeHtml(x.getFullname()) + "</li>\n")
                .collect(Collectors.joining());

        return Response.htmlOk(nameEntryTemplate.renderTemplate(Map.of("names", names)));
    }

    public Response testform(Request r) {
        final var authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return new Response(_401_UNAUTHORIZED);
        }

        final var nameEntry = r.body().asString("name_entry");

        final var newPersonName = new PersonName(0L, nameEntry);
        ddps.persistToDisk(newPersonName);
        return new Response(_303_SEE_OTHER, List.of("Location: formentry"));
    }

    /**
     * This is an example of a homepage for a domain.  Here we examine
     * whether the user is authenticated.  If not, we request them to
     * log in.  If already, then we show some features and the log-out link.
     */
    public Response sampleDomainIndex(Request request) {
        final var authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return Response.htmlOk(unauthHomepage);
        } else {
            return Response.htmlOk(authHomepage);
        }

    }

}

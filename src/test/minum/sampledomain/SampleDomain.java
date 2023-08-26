package minum.sampledomain;

import minum.Context;
import minum.auth.AuthUtils;
import minum.database.Db;
import minum.templating.TemplateProcessor;
import minum.utils.FileUtils;
import minum.utils.StringUtils;
import minum.web.Request;
import minum.web.Response;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import static minum.web.StatusLine.StatusCode.*;


public class SampleDomain {

    private final Db<PersonName> db;
    private final AuthUtils auth;
    private final TemplateProcessor nameEntryTemplate;
    private final String authHomepage;
    private final String unauthHomepage;
    private final FileUtils fileUtils;

    public SampleDomain(Db<PersonName> diskData, AuthUtils auth, Context context) {
        this.db = diskData;
        this.auth = auth;
        this.fileUtils = context.getFileUtils();
        nameEntryTemplate = TemplateProcessor.buildProcessor(fileUtils.readTextFile("out/templates/sampledomain/name_entry.html"));
        authHomepage = fileUtils.readTextFile("out/templates/sampledomain/auth_homepage.html");
        unauthHomepage = fileUtils.readTextFile("out/templates/sampledomain/unauth_homepage.html");
    }

    public Response formEntry(Request r) {
        final var authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return new Response(_401_UNAUTHORIZED);
        }
        final String names = db
                .values().stream().sorted(Comparator.comparingLong(PersonName::getIndex))
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
        db.write(newPersonName);
        return new Response(_303_SEE_OTHER, Map.of("Location","formentry"));
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

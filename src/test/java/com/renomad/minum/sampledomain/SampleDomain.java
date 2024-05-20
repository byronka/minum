package com.renomad.minum.sampledomain;

import com.renomad.minum.Context;
import com.renomad.minum.TheRegister;
import com.renomad.minum.auth.AuthUtils;
import com.renomad.minum.database.Db;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.FullSystem;
import com.renomad.minum.web.Request;
import com.renomad.minum.web.Response;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import static com.renomad.minum.web.StatusLine.StatusCode.*;


public class SampleDomain {

    public static void main(String[] args) {
        // Start the system
        FullSystem fs = FullSystem.initialize();

        // Register some endpoints
        new TheRegister(fs.getContext()).registerDomains();

        fs.block();
    }

    private final Db<PersonName> db;
    private final AuthUtils auth;
    private final TemplateProcessor nameEntryTemplate;
    private final String authHomepage;
    private final String unauthHomepage;
    private final FileUtils fileUtils;

    public SampleDomain(Db<PersonName> db, AuthUtils auth, Context context) {
        this.db = db;
        this.auth = auth;
        this.fileUtils = context.getFileUtils();
        String nameEntryTemplateString = fileUtils.readTextFile("src/test/webapp/templates/sampledomain/name_entry.html");
        nameEntryTemplate = TemplateProcessor.buildProcessor(nameEntryTemplateString);
        authHomepage = fileUtils.readTextFile("src/test/webapp/templates/sampledomain/auth_homepage.html");
        unauthHomepage = fileUtils.readTextFile("src/test/webapp/templates/sampledomain/unauth_homepage.html");
    }

    public Response formEntry(Request r) {
        final var authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return new Response(CODE_401_UNAUTHORIZED);
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
            return new Response(CODE_401_UNAUTHORIZED);
        }

        final var nameEntry = r.body().asString("name_entry");

        final var newPersonName = new PersonName(0L, nameEntry);
        db.write(newPersonName);
        return new Response(CODE_303_SEE_OTHER, Map.of("Location","formentry"));
    }

    /**
     * a GET request, at /hello?name=foo
     * <p>
     *     Replies "hello foo"
     * </p>
     */
    public Response helloName(Request request) {
        String name = request.requestLine().queryString().get("name");
        return Response.htmlOk("hello " + name);
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

    public Response throwException(Request request) {
        throw new RuntimeException("This is a test of the business logic throwing an exception");
    }
}

package minum.sampledomain;

import minum.Context;
import minum.auth.AuthUtils;
import minum.database.DatabaseDiskPersistenceSimpler;
import minum.utils.StringUtils;
import minum.web.Request;
import minum.web.Response;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static minum.database.SimpleIndexed.calculateNextIndex;
import static minum.web.StatusLine.StatusCode.*;


public class SampleDomain {

    private final DatabaseDiskPersistenceSimpler<PersonName> ddps;
    private final List<PersonName> personNames;
    private final AuthUtils auth;
    private final AtomicLong newPersonIndex;
    private final Context context;
    private final StringUtils stringUtils;

    public SampleDomain(DatabaseDiskPersistenceSimpler<PersonName> diskData, AuthUtils auth, Context context) {
        this.ddps = diskData;
        personNames = diskData.readAndDeserialize(PersonName.EMPTY);
        this.auth = auth;
        this.context = context;
        this.stringUtils = new StringUtils(context);
        newPersonIndex = new AtomicLong(calculateNextIndex(personNames));
    }

    public Response formEntry(Request r) {
        final var authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return new Response(_401_UNAUTHORIZED);
        }
        final String names = personNames
                .stream().sorted(Comparator.comparingLong(PersonName::getIndex))
                .map(x -> "<li>" + stringUtils.safeHtml(x.getFullname()) + "</li>\n")
                .collect(Collectors.joining());

        return Response.htmlOk("""
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>Name entry | The sample domain</title>
                        <meta charset="utf-8"/>
                        <link rel="stylesheet" href="main.css" />
                    </head>
                    <body>
                        <p>
                            <a href="index.html" >Index</a>
                        </p>
                        <form method="post" action="testform">
                            <label for="name_entry">Name Entry
                                <input name="name_entry" id="name_entry" type="text" value="" autofocus="autofocus" />
                            </label>
                            <button>Enter</button>
                        </form>
                        <ol>
                        %s
                        </ol>
                    </body>
                </html>
                """.formatted(names));
    }

    public Response testform(Request r) {
        final var authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return new Response(_401_UNAUTHORIZED);
        }

        final var nameEntry = r.body().asString("name_entry");

        final var newPersonName = new PersonName(newPersonIndex.getAndIncrement(), nameEntry, context);
        personNames.add(newPersonName);
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
            return Response.htmlOk("""
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>Unauthenticated | The sample domain</title>
                        <meta charset="utf-8"/>
                        <link rel="stylesheet" href="main.css" />
                    </head>
                    <body>
                        <p>There's nothing here to see.  Try logging in.</p>
                    </body>
                </html>
                """);
        } else {
            return Response.htmlOk("""
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>Authenticated homepage | The sample domain</title>
                        <meta charset="utf-8"/>
                        <link rel="stylesheet" href="main.css" />
                    </head>
                    <body>
                        <p><a href="formEntry">Enter a name</a></p>
                    </body>
                </html>
                """);
        }

    }

}

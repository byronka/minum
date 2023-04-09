package atqa.sampledomain;

import atqa.auth.AuthUtils;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.utils.StringUtils;
import atqa.web.*;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static atqa.database.SimpleIndexed.calculateNextIndex;
import static atqa.utils.StringUtils.byteArrayToString;
import static atqa.web.StatusLine.StatusCode.*;

public class SampleDomain {

    private final DatabaseDiskPersistenceSimpler<PersonName> ddps;
    private final List<PersonName> personNames;
    private final AuthUtils auth;
    private final AtomicLong newPersonIndex;

    public SampleDomain(DatabaseDiskPersistenceSimpler<PersonName> diskData, AuthUtils auth) {
        this.ddps = diskData;
        personNames = diskData.readAndDeserialize(PersonName.EMPTY);
        this.auth = auth;

        newPersonIndex = new AtomicLong(calculateNextIndex(personNames));
    }

    public Response formEntry(Request r) {
        final var authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return new Response(_401_UNAUTHORIZED, List.of("Content-Type: text/html; charset=UTF-8"));
        }
        final String names = personNames
                .stream().sorted(Comparator.comparingLong(PersonName::index))
                .map(x -> "<li>" + StringUtils.safeHtml(x.fullname()) + "</li>\n")
                .collect(Collectors.joining());

        return new Response(_200_OK, List.of("Content-Type: text/html; charset=UTF-8"), """
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>Name entry | The sample domain</title>
                        <meta charset="utf-8"/>
                        <link rel="stylesheet" href="main.css" />
                    </head>
                    <body>
                        <form method="post" action="testform">
                            <label for="name_entry">Name Entry
                                <input name="name_entry" id="name_entry" type="text" value="" />
                            </label>
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
            return new Response(_401_UNAUTHORIZED, List.of("Content-Type: text/html; charset=UTF-8"));
        }

        final var nameEntry = byteArrayToString(r.bodyMap().get("name_entry"));

        final var newPersonName = new PersonName(newPersonIndex.getAndIncrement(), nameEntry);
        personNames.add(newPersonName);
        ddps.persistToDisk(newPersonName);
        return new Response(_303_SEE_OTHER, List.of("Content-Type: text/html; charset=UTF-8", "Location: formentry"));
    }

    /**
     * This is an example of a homepage for a domain.  Here we examine
     * whether the user is authenticated.  If not, we request them to
     * log in.  If already, then we show some features and the log-out link.
     */
    public Response sampleDomainIndex(Request request) {
        final var authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return new Response(_200_OK, List.of("Content-Type: text/html; charset=UTF-8"), """
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>Unauthenticated | The sample domain</title>
                        <meta charset="utf-8"/>
                        <link rel="stylesheet" href="main.css" />
                    </head>
                    <body>
                    <p><a href="register">Register user</a></p>
                    <p><a href="login">Sign in</a></p>
                    </body>
                </html>
                """);
        } else {
            return new Response(_200_OK, List.of("Content-Type: text/html; charset=UTF-8"), """
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>Authenticated homepage | The sample domain</title>
                        <meta charset="utf-8"/>
                        <link rel="stylesheet" href="main.css" />
                    </head>
                    <body>
                    <p><a href="formEntry">Enter a name</a></p>
                    <p><a href="logout">Sign out</a></p>
                    </body>
                </html>
                """);
        }

    }

}

package atqa.sampledomain;

import atqa.auth.AuthUtils;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.utils.StringUtils;
import atqa.web.*;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static atqa.auth.RegisterResultStatus.*;
import static atqa.database.SimpleIndexed.calculateNextIndex;
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
            return new Response(_401_UNAUTHORIZED, ContentType.TEXT_HTML, Collections.emptyList());
        }
        final String names = personNames
                .stream().sorted(Comparator.comparingLong(PersonName::index))
                .map(x -> "<li>" + StringUtils.safeHtml(x.fullname()) + "</li>\n")
                .collect(Collectors.joining());

        return new Response(_200_OK, ContentType.TEXT_HTML, """
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>This is the title</title>
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
            return new Response(_401_UNAUTHORIZED, ContentType.TEXT_HTML, Collections.emptyList());
        }

        final var nameEntry = (String) r.bodyMap().get("name_entry");

        final var newPersonName = new PersonName(newPersonIndex.getAndIncrement(), nameEntry);
        personNames.add(newPersonName);
        ddps.persistToDisk(newPersonName);
        return new Response(_303_SEE_OTHER, ContentType.TEXT_HTML, List.of("Location: formentry"));
    }

    public Response registerUser(Request r) {
        final var authResult = auth.processAuth(r);
        if (authResult.isAuthenticated()) {
            return new Response(_303_SEE_OTHER, List.of("Location: formentry"));
        }

        final var username = (String) r.bodyMap().get("username");
        final var password = (String) r.bodyMap().get("password");
        final var registrationResult = auth.registerUser(username, password);

        if (registrationResult.status() == ALREADY_EXISTING_USER) {
            return new Response(_200_OK, ContentType.TEXT_PLAIN, "This user is already registered");
        }
        return new Response(_303_SEE_OTHER, List.of("Location: formentry"));

    }

    public Response loginUser(Request r) {
        final var authResult = auth.processAuth(r);
        if (authResult.isAuthenticated()) {
            return new Response(_303_SEE_OTHER, List.of("Location: formentry"));
        }

        final var username = (String) r.bodyMap().get("username");
        final var password = (String) r.bodyMap().get("password");
        final var loginResult = auth.loginUser(username, password);
        switch (loginResult.status()) {
            case SUCCESS -> {
                final var sessionId = auth.registerNewSession();
                return new Response(_303_SEE_OTHER, List.of("Location: formentry", "Set-Cookie: " + sessionId.sessionCode()));
            }
            case DID_NOT_MATCH_PASSWORD -> {
                return new Response(_401_UNAUTHORIZED, ContentType.TEXT_PLAIN, "Invalid account credentials");
            }
        }
        return new Response(_303_SEE_OTHER, List.of("Location: formentry"));
    }
}

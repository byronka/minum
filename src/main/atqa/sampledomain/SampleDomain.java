package atqa.sampledomain;

import atqa.auth.AuthUtils;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.utils.StringUtils;
import atqa.web.ContentType;
import atqa.web.WebFramework;
import atqa.web.Request;
import atqa.web.Response;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static atqa.database.SimpleIndexed.calculateNextIndex;
import static atqa.web.StatusLine.StatusCode._200_OK;
import static atqa.web.StatusLine.StatusCode._303_SEE_OTHER;

public class SampleDomain {

    private final DatabaseDiskPersistenceSimpler<PersonName> ddps;
    private final List<PersonName> personNames;
    private final AuthUtils auth;
    final AtomicLong newPersonIndex;

    public SampleDomain(DatabaseDiskPersistenceSimpler<PersonName> diskData, AuthUtils auth) {
        this.ddps = diskData;
        personNames = diskData.readAndDeserialize(new PersonName("",0L));
        this.auth = auth;

        newPersonIndex = new AtomicLong(calculateNextIndex(personNames));
    }

    /*
    our web methods must match a particular pattern.
    Specifically, Function<Request, Response>

    In this case however, if we are not using the value of the parameter, "r", then
    the IDE will complain that it's unused.  But it has to remain to match the pattern.
     */
    @SuppressWarnings("unused")
    public Response formEntry(Request r) {
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
//        auth.authenticatedOrRedirect(authResult);
        final var formData = WebFramework.parseUrlEncodedForm(r.body());
        final var nameEntry = formData.get("name_entry");

        final var newPersonName = new PersonName(nameEntry, newPersonIndex.getAndAdd(1));
        personNames.add(newPersonName);
        ddps.persistToDisk(newPersonName);
        return new Response(_303_SEE_OTHER, ContentType.TEXT_HTML, List.of("Location: formentry"));
    }

}

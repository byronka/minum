package atqa.sampledomain;

import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.ILogger;
import atqa.web.ContentType;
import atqa.web.Request;
import atqa.web.Response;
import atqa.web.Frame;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static atqa.web.StatusLine.StatusCode._200_OK;
import static atqa.web.StatusLine.StatusCode._303_SEE_OTHER;

public class SampleDomain {

    private final DatabaseDiskPersistenceSimpler<PersonName> ddps;
    private final List<PersonName> personNames;
    final AtomicLong newPersonIndex;

    public SampleDomain(ExecutorService es, ILogger logger) throws IOException {
        ddps = new DatabaseDiskPersistenceSimpler<>("out/simple_db/names", es, logger);
        personNames = ddps.readAndDeserialize(new PersonName("",0L));
        final var newPersonIndexTemp = personNames
                .stream()
                .max(Comparator.comparingLong(PersonName::index))
                .map(PersonName::index)
                .orElse(0L) + 1L;
        newPersonIndex = new AtomicLong(newPersonIndexTemp);
    }

    public Response formEntry(Request r) {
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
                    </body>
                </html>
                """);
    }

    public Response testform(Request r) {
        final var formData = Frame.parseUrlEncodedForm(r.body());
        final var nameEntry = formData.get("name_entry");

        final var newPersonName = new PersonName(nameEntry, newPersonIndex.getAndAdd(1));
        personNames.add(newPersonName);
        ddps.persistToDisk(newPersonName);
        return new Response(_303_SEE_OTHER, ContentType.TEXT_HTML, List.of("Location: shownames"));
    }

    public Response showNames(Request r) {
        final String names = personNames
                .stream().sorted(Comparator.comparingLong(PersonName::index))
                .map(x -> "<li>" + x.fullname() + "</li>\n")
                .collect(Collectors.joining());

        return new Response(_200_OK, ContentType.TEXT_HTML, """
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>This is the title</title>
                    </head>
                    
                    <body>
                        <ol>
                """+
                names
                +"""
                        </ol>
                    </body>
                </html>
                """);
    }

}

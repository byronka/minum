package atqa.sampledomain;

import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.ILogger;
import atqa.web.ContentType;
import atqa.web.WebFramework;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static atqa.web.StatusLine.StatusCode._200_OK;
import static atqa.web.StatusLine.StatusCode._303_SEE_OTHER;

public class SampleDomain {

    private final ExecutorService es;
    private final ILogger logger;

    public SampleDomain(ExecutorService es, ILogger logger) {
        this.es = es;
        this.logger = logger;
    }

    public WebFramework.Response formEntry(WebFramework.Request r) {
        return new WebFramework.Response(_200_OK, ContentType.TEXT_HTML, """
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>This is the title</title>
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

    public WebFramework.Response testform(WebFramework.Request r) {
        final var ddps1 =
                new DatabaseDiskPersistenceSimpler<PersonName>("out/simple_db/names", es, logger);
        final var personNames = ddps1.readAndDeserialize(new PersonName("",0L));
        final var data = r.body();
        final var formData = WebFramework.parseUrlEncodedForm(data);
        final var nameEntry = formData.get("name_entry");
        final var newIndex = personNames.stream()
                .max(Comparator.comparingLong(PersonName::index))
                .map(PersonName::index).orElse(0L) + 1L;
        final var newPersonName = new PersonName(nameEntry, newIndex);
        personNames.add(newPersonName);
        ddps1.persistToDisk(newPersonName);
        return new WebFramework.Response(_303_SEE_OTHER, ContentType.TEXT_HTML, List.of("Location: pageone"));
    }

}

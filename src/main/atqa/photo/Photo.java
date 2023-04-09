package atqa.photo;

import atqa.auth.AuthUtils;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.web.Request;
import atqa.web.Response;
import atqa.web.StatusLine;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static atqa.database.SimpleIndexed.calculateNextIndex;
import static atqa.utils.Invariants.mustBeTrue;

public class Photo {
    private final DatabaseDiskPersistenceSimpler<Photograph> diskData;
    private final AuthUtils auth;
    private final List<Photograph> photographs;
    private final AtomicLong newPhotographIndex;

    public Photo(DatabaseDiskPersistenceSimpler<Photograph> diskData, AuthUtils auth) {
        this.diskData = diskData;
        photographs = diskData.readAndDeserialize(Photograph.EMPTY);
        this.auth = auth;
        newPhotographIndex = new AtomicLong(calculateNextIndex(photographs));
    }

    public Response receivePhoto(Request request) {
        final var photo = request.bodyMap().get("photo");
        final var description = new String(request.bodyMap().get("description"));
        final var newUrl = "myPhotoUrl";
        final Photograph p = new Photograph(newPhotographIndex.getAndIncrement(), photo, newUrl, description);
        photographs.add(p);
        diskData.persistToDisk(p);
        return new Response(StatusLine.StatusCode._200_OK, List.of("Content-Type: text/plain"), newUrl.getBytes(StandardCharsets.UTF_8));
    }

    public Response viewPhoto(Request request) {
        final var photoUrl = request.startLine().pathDetails().isolatedPath();
        final var myPhoto = photographs.stream().filter(x -> x.photoUrl().equals(photoUrl)).toList();
        mustBeTrue(myPhoto.size() == 0 || myPhoto.size() == 1, "there must be either 0 or 1 photos found with the url " + photoUrl);
        if (myPhoto.size() == 0) {
            return new Response(StatusLine.StatusCode._404_NOT_FOUND, List.of("Content-Type: text/plain"), "");
        }
        return new Response(StatusLine.StatusCode._200_OK, List.of("Content-Type: image/jpeg"), myPhoto.get(0).photo());
    }
}

package atqa.photo;

import atqa.auth.AuthUtils;
import atqa.auth.SessionId;
import atqa.auth.User;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.TestLogger;
import atqa.web.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static atqa.framework.TestFramework.assertEquals;

public class PhotoTests {
    private final TestLogger logger;

    public PhotoTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) {
        final Photo p = setupPhotoClass(es);

        // Basic API functionality - receiving and viewing a photo
        final byte[] imagesBytes={0xa, 0x2, 0x03, (byte) 0xF2};

        logger.test("A user should be able to send a photo"); {
            final Request photoSubmitRequest = buildPhotoSubmitRequest(imagesBytes);
            final Response photoSubmitResponse = p.receivePhoto(photoSubmitRequest);
            final String submitBody = new String(photoSubmitResponse.body(), StandardCharsets.UTF_8);
            assertEquals(submitBody, "myPhotoUrl");

        logger.test("A user should be able to view an uploaded photo");
            final Request photoReceiveRequest = buildPhotoViewRequest(new String(photoSubmitResponse.body(), StandardCharsets.UTF_8));
            final Response photoReceiveResponse = p.viewPhoto(photoReceiveRequest);
            final byte[] receiveBody = photoReceiveResponse.body();
            final var resultBytesList = List.of(receiveBody);
            final var expectedBytesList = List.of(imagesBytes);
            assertEquals(resultBytesList, expectedBytesList);
        }
    }

    /**
     * Request information for a particular photo.
     * In this case, our path indicates "photo1", so we return photo1
     */
    private Request buildPhotoViewRequest(String photoUrl) {
        return new Request(
                new Headers(0, ContentType.NONE, Collections.emptyList()),
                new StartLine(
                        StartLine.Verb.GET,
                        new StartLine.PathDetails(photoUrl, "", Map.of()),
                        WebEngine.HttpVersion.ONE_DOT_ONE,
                        ""),
                "".getBytes(StandardCharsets.UTF_8),
                Map.of());
    }

    private Photo setupPhotoClass(ExecutorService es) {
        final var photoDdps = new DatabaseDiskPersistenceSimpler<Photograph>("out/simple_db/photos", es, logger);
        final var authUtils = setupAuthUtils(es, logger);
        return new Photo(photoDdps, authUtils);
    }

    private static AuthUtils setupAuthUtils(ExecutorService es, TestLogger logger) {
        final var sessionDdps = new DatabaseDiskPersistenceSimpler<SessionId>("out/simple_db/sessionsphotos", es, logger);
        final var userDdps = new DatabaseDiskPersistenceSimpler<User>("out/simple_db/usersphotos", es, logger);
        return new AuthUtils(sessionDdps, userDdps, logger);
    }

    /**
     * Simulate sending a photo file to the server
     */
    private Request buildPhotoSubmitRequest(byte[] imageBytes) {
        return new Request(
                new Headers(0, ContentType.NONE, Collections.emptyList()),
                null,
                imageBytes,
                Map.of("photo", imageBytes, "description", "bar bar"));
    }
}

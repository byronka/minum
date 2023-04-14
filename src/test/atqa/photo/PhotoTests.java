package atqa.photo;

import atqa.auth.AuthUtils;
import atqa.auth.SessionId;
import atqa.auth.User;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.logging.TestLogger;
import atqa.utils.StringUtils;
import atqa.web.*;

import java.io.IOException;
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

    public void tests(ExecutorService es) throws IOException {
        final Photo p = setupPhotoClass(es);

        // Basic API functionality - receiving and viewing a photo
        final byte[] imagesBytes = PhotoTests.class.getClassLoader().getResource("testresources/david_playing_chess.jpeg").openStream().readAllBytes();

        logger.test("A user should be able to send a photo"); {
            final Request photoSubmitRequest = buildPhotoSubmitRequest(imagesBytes);
            final Response photoSubmitResponse = p.receivePhoto(photoSubmitRequest);
            final String submitBody = StringUtils.byteArrayToString(photoSubmitResponse.body());
            assertEquals(submitBody, "myPhotoUrl");

        logger.test("A user should be able to view an uploaded photo");
            final Request photoReceiveRequest = buildPhotoViewRequest(StringUtils.byteArrayToString(photoSubmitResponse.body()));
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
                new Headers(Collections.emptyList()),
                new StartLine(
                        StartLine.Verb.GET,
                        new StartLine.PathDetails(photoUrl, "", Map.of()),
                        WebEngine.HttpVersion.ONE_DOT_ONE,
                        ""),
                new Request.Body(Map.of()));
    }

    private Photo setupPhotoClass(ExecutorService es) {
        final var photoDdps = new DatabaseDiskPersistenceSimpler<Photograph>("out/simple_db/phototests/photos", es, logger);
        final var authUtils = setupAuthUtils(es, logger);
        return new Photo(photoDdps, authUtils);
    }

    private static AuthUtils setupAuthUtils(ExecutorService es, TestLogger logger) {
        final var sessionDdps = new DatabaseDiskPersistenceSimpler<SessionId>("out/simple_db/phototests/sessions", es, logger);
        final var userDdps = new DatabaseDiskPersistenceSimpler<User>("out/simple_db/phototests/users", es, logger);
        return new AuthUtils(sessionDdps, userDdps, logger);
    }

    /**
     * Simulate sending a photo file to the server
     */
    private Request buildPhotoSubmitRequest(byte[] imageBytes) {
        return new Request(
                new Headers(Collections.emptyList()),
                null,
                new Request.Body(Map.of("photo", imageBytes, "description", "bar bar".getBytes(StandardCharsets.UTF_8))));
    }
}

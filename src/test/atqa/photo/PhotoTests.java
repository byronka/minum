package atqa.photo;

import atqa.logging.TestLogger;
import atqa.web.StatusLine;

import java.util.concurrent.ExecutorService;

import static atqa.framework.TestFramework.assertEquals;

public class PhotoTests {
    private final TestLogger logger;

    public PhotoTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) {
        final var p = setupPhotoClass();

        logger.test("A user should be able to send a photo"); {
            final var photoRequest = buildPhotoRequest();
            final var response = p.receivePhoto(photoRequest);
            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
        }
    }
}

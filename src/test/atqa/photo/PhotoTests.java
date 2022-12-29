package atqa.photo;

import atqa.logging.TestLogger;
import atqa.web.Headers;
import atqa.web.Request;
import atqa.web.Response;
import atqa.web.StatusLine;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static atqa.framework.TestFramework.assertEquals;

public class PhotoTests {
    private final TestLogger logger;

    public PhotoTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) {
        final Photo p = setupPhotoClass();

        // Basic API functionality - receiving and viewing a photo
        final byte[] imagesBytes={0xa, 0x2, 0x03};

        logger.test("A user should be able to send a photo"); {
            final Request photoRequest = buildPhotoSubmitRequest(imagesBytes);
            final Response response = p.receivePhoto(photoRequest);
            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
        }

        logger.test("A user should be able to view an uploaded photo"); {
            final Request photoRequest = buildPhotoViewRequest();
            final Response response = p.viewPhoto(photoRequest);
            assertEquals(response.body(), imagesBytes);
        }
    }

    /**
     * Request information for a particular photo
     */
    private Request buildPhotoViewRequest() {
        return new Request(new Headers(0, Collections.emptyList()), null, "", Map.of());
    }

    private Photo setupPhotoClass() {
        return new Photo();
    }

    /**
     * Simulate sending a photo file to the server
     */
    private Request buildPhotoSubmitRequest(byte[] imageBytes) {
        return new Request(new Headers(0, Collections.emptyList()), null, imageBytes, Map.of());
    }
}

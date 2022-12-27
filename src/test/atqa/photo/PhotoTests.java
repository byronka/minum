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

        logger.test("A user should be able to send a photo"); {
            final Request photoRequest = buildPhotoRequest();
            final Response response = p.receivePhoto(photoRequest);
            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
        }
    }

    private Photo setupPhotoClass() {
        return new Photo();
    }

    private Request buildPhotoRequest() {
        return new Request(new Headers(0, Collections.emptyList()), null, "abc=123", Map.of());
    }
}

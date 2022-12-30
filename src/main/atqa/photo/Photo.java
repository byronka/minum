package atqa.photo;

import atqa.web.Request;
import atqa.web.Response;

public class Photo {
    public Response receivePhoto(Request request) {
        final var urlEncodedPhoto = request.bodyMap().get("photo");
        final var urlEncodedDescription = request.bodyMap().get("description");
        return null;
    }

    public Response viewPhoto(Request request) {
        return null;
    }
}

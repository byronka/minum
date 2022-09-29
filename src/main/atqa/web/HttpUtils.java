package atqa.web;

import java.io.IOException;

public class HttpUtils {

    public static String readBody(SocketWrapper sw, int length) throws IOException {
        return sw.readByLength(length);
    }

}

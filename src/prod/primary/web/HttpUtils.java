package primary.web;

public class HttpUtils {

    public static String readBody(Web.SocketWrapper sw, int length) {
        return sw.readByLength(length);
    }

}

package primary.web;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import static utils.Invariants.requireNotNull;

public class HttpUtils {

    /**
     * This is the brains of how the server responds to web clients
     */
    public static final Consumer<Web.SocketWrapper> serverHandler = (sw) -> {
        StartLine sl = StartLine.extractStartLine(sw.readLine());
        HeaderInformation hi = HeaderInformation.extractHeaderInformation(sw);

        int aValue = Integer.parseInt(sl.getQueryString().get("a"));
        int bValue = Integer.parseInt(sl.getQueryString().get("b"));
        int sum = aValue + bValue;
        String sumString = String.valueOf(sum);

        sw.sendHttpLine("HTTP/1.1 200 OK");
        String date = ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        sw.sendHttpLine("Date: " + date);
        sw.sendHttpLine("Server: atqa");
        sw.sendHttpLine("Content-Type: text/plain; charset=UTF-8");
        sw.sendHttpLine("Content-Length: " + sumString.length());
        sw.sendHttpLine("");
        sw.sendHttpLine(sumString);
    };

    public static String readBody(Web.SocketWrapper sw, int length) {
        return sw.readByLength(length);
    }

    /**
     * Encodes UTF-8 text using URL-encoding
     */
    public static String encode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }

    /**
     * Decodes URL-encoded UTF-8 text
     */
    public static String decode(String str) {
        requireNotNull(str);
        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }
}

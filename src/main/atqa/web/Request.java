package atqa.web;

import java.util.Map;

/**
 * An HTTP request
 * @param bodyMap key-value pairs derived from the body
 */
public record Request(Headers headers, StartLine startLine, Map<String, byte[]> bodyMap){
}

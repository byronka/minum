package atqa.web;

/**
 * An HTTP request
 */
public record Request(Headers headers, StartLine startLine, Body body,
                      /*
                      This is the remote address making the request
                       */
                      String remoteRequester) {

}

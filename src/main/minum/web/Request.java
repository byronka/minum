package minum.web;

/**
 * An HTTP request.
 * <p>
 * From <a href="https://en.wikipedia.org/wiki/HTTP#Request_syntax">Wikipedia</a>
 * </p>
 *<p>
 *     A client sends request messages to the server, which consist of:
 *</p>
 * <ul>
 *     <li>
 *      a request line, consisting of the case-sensitive request
 *      method, a space, the requested URL, another space, the
 *      protocol version, a carriage return, and a line feed, e.g.:
 *      <pre>
 *          GET /images/logo.png HTTP/1.1
 *      </pre>
 *      </li>
 *
 *      <li>
 *      zero or more request header fields (at least 1 or more
 *      headers in case of HTTP/1.1), each consisting of the case-insensitive
 *      field name, a colon, optional leading whitespace, the field
 *      value, an optional trailing whitespace and ending with a
 *      carriage return and a line feed, e.g.:
 *
 *      <pre>
 *      Host: www.example.com
 *      Accept-Language: en
 *      </pre>
 *      </li>
 *
 *      <li>
 *      an empty line, consisting of a carriage return and a line feed;
 *      </li>
 *
 *      <li>
 *      an optional message body.
 *      </li>
 *      In the HTTP/1.1 protocol, all header fields except Host: hostname are optional.
 * </ul>
 *
 * <p>
 * A request line containing only the path name is accepted by servers to
 * maintain compatibility with HTTP clients before the HTTP/1.0 specification in RFC 1945.
 *</p>
 */
public record Request(Headers headers, StartLine startLine, Body body,
                      /*
                      This is the remote address making the request
                       */
                      String remoteRequester) {

}

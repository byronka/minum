/**
 * This package provides HTTP capabilities.
 * <br>
 * <div style="border: 5px solid red; margin-top: 10px; margin-bottom: 10px; padding: 2px;">
 * <h3>Most valuable for those new to Minum:</h3>
 * <ol>
 *     <li><a href="WebFramework.html">WebFramework</a> - Provides methods to register endpoints</li>
 *     <li>The <a href="IRequest.html">IRequest</a> interface for HTTP requests, primarily the request line (including query strings), headers, and body</li>
 *     <li>The <a href="Response.html">Response</a> class, which provides methods to build an HTTP response</li>
 * </ol>
 * <p>
 *     Most other classes provide capabilities to branch off those core concepts.
 * </p>
 * </div>
 * <p>
 * Here is a typical "main" method for an application. The important thing to note is we are initializing {@link com.renomad.minum.web.FullSystem} and
 * using it to register endpoints.  A more organized approach is to put the endpoint registrations
 * into another file.  See the example project in the Minum codebase or any of the other example projects.
 * </p>
 * <pre>
 * {@code
 * package org.example;
 *
 * import com.renomad.minum.web.FullSystem;
 * import com.renomad.minum.web.Response;
 *
 * import static com.renomad.minum.web.RequestLine.Method.GET;
 *
 * public class Main {
 *
 *     public static void main(String[] args) {
 *         FullSystem fs = FullSystem.initialize();
 *         fs.getWebFramework().registerPath(GET, "", request -> Response.htmlOk("<p>Hi there world!</p>"));
 *         fs.block();
 *     }
 * }
 * }
 * </pre>
 * <p>
 *  Here's an example of a business-related function using authentication and the Minum database.  This
 *  code is extracted from the SampleDomain.java file in the src/test directory:
 * </p>
 * <pre>{@code
 *       public IResponse formEntry(IRequest r) {
 *         final var authResult = auth.processAuth(r);
 *         if (! authResult.isAuthenticated()) {
 *             return Response.buildLeanResponse(CODE_401_UNAUTHORIZED);
 *         }
 *         final String names = db
 *                 .values().stream().sorted(Comparator.comparingLong(PersonName::getIndex))
 *                 .map(x -> "<li>" + StringUtils.safeHtml(x.getFullname()) + "</li>\n")
 *                 .collect(Collectors.joining());
 *
 *         return Response.htmlOk(nameEntryTemplate.renderTemplate(Map.of("names", names)));
 *     }
 *     }
 *  </pre>
 */
package com.renomad.minum.web;
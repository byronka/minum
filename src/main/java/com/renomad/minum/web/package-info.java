/**
 * Code and data for HTTP web serving.
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
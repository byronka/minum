/**
 * These are the utilities needed to enable a database for the application.
 * <p>
 *     The performance is excellent.  The following simplified example is extracted from SampleDomain.java
 *     and TheRegister.java, both in the tests directory.
 * </p>
 * <p>
 *     This is also a good example of a few tangential capabilities of the Minum framework,
 *     including how a developer is expected to "clean" html before sending it, handling
 *     authentication, and getting data from the database.
 * </p>
 * <pre>
 * {@code
 * //-------------------
 * // TheRegister.java
 * //-------------------
 * private SampleDomain setupSampleDomain(AuthUtils auth) {
 *     Db<PersonName> sampleDomainDb = context.getDb("names", PersonName.EMPTY);
 *     return new SampleDomain(sampleDomainDb, auth, context);
 * }
 *
 * var sd = setupSampleDomain(auth);
 * webFramework.registerPath(GET, "formentry", sd::formEntry);
 *
 * //-------------------
 * //  SampleDomain.java
 * //-------------------
 * public SampleDomain(Db<PersonName> db, AuthUtils auth, Context context) {
 *     this.db = db;
 *     this.auth = auth;
 *     this.fileUtils = context.getFileUtils();
 *     String nameEntryTemplateString = fileUtils.readTextFile("src/test/webapp/templates/sampledomain/name_entry.html");
 *     nameEntryTemplate = TemplateProcessor.buildProcessor(nameEntryTemplateString);
 * }
 *
 * public Response formEntry(Request r) {
 *     final var authResult = auth.processAuth(r);
 *     if (! authResult.isAuthenticated()) {
 *         return new Response(CODE_401_UNAUTHORIZED);
 *     }
 *     final String names = db
 *             .values().stream().sorted(Comparator.comparingLong(PersonName::getIndex))
 *             .map(x -> "<li>" + StringUtils.safeHtml(x.getFullname()) + "</li>\n")
 *             .collect(Collectors.joining());
 *
 *     return Response.htmlOk(nameEntryTemplate.renderTemplate(Map.of("names", names)));
 * }
 * }
 * </pre>
 */
package com.renomad.minum.database;
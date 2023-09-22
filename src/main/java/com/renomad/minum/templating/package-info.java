/**
 * templating abilities, mostly for html but useful
 * for any situation requiring substitution inside text.
 * <p>
 *     Performance is excellent.
 * </p>
 * <p>
 *     Here is a slightly simplified example extracted from SampleDomain.java in the tests directory
 * </p>
 *
 * <pre>
 * {@code
 *
 *  private final TemplateProcessor nameEntryTemplate;
 *
 *  public SampleDomain(Context context) {
 *      this.fileUtils = context.getFileUtils();
 *      String nameEntryTemplateString = fileUtils.readTextFile("src/test/webapp/templates/sampledomain/name_entry.html");
 *      nameEntryTemplate = TemplateProcessor.buildProcessor(nameEntryTemplateString);
 *  }
 *
 *  public Response formEntry(Request r) {
 *      final String names = db
 *              .values().stream().sorted(Comparator.comparingLong(PersonName::getIndex))
 *              .map(x -> "<li>" + StringUtils.safeHtml(x.getFullname()) + "</li>\n")
 *              .collect(Collectors.joining());
 *
 *      return Response.htmlOk(nameEntryTemplate.renderTemplate(Map.of("names", names)));
 *  }
 * }
 * </pre>
 */
package com.renomad.minum.templating;
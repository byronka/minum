package com.renomad.minum.templating;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class provides methods for working with templates.
 * <p>
 * The first step is to write a template.  Here is an example:
 * </p>
 * <pre>
 * Hello, my name is {{name}}
 * </pre>
 * <p>
 * Then, feed that string into the {@link #buildProcessor} method, like
 * this:
 * </p>
 * <pre>
 * {@code
 *   String input = "Hello, my name is {{name}}"
 *   TemplateProcessor helloProcessor = TemplateProcessor.buildProcessor(input);
 * }
 * </pre>
 * <p>
 * The returned value ("helloProcessor") can be rendered with different values. For
 * example:
 * </p>
 * <pre>
 * {@code
 *   Map<String,String> myMap = Map.of("name", "Susanne");
 *   String fullyRenderedString = helloProcessor.renderTemplate(myMap);
 * }
 * </pre>
 * <p>
 *     The result is:
 * </p>
 * <pre>
 *     {@code Hello, my name is Susanne}
 * </pre>
 */
public final class TemplateProcessor {

    /**
     * template sections by indentation
     */
    private final List<TemplateSection> templateSections;
    private final Set<String> keysFoundInTemplate;
    private final String originalText;
    /**
     * This value is used to calculate a quick estimate of how many
     * bytes of memory we will need for the buffer holding our generated string
     */
    private final static double SIZE_ESTIMATE_MODIFIER = 1.1;
    private final int estimatedSize;

    /**
     * Instantiate a new object with a list of {@link TemplateSection}.
     */
    private TemplateProcessor(List<TemplateSection> templateSections, String originalText) {
        this.templateSections = templateSections;
        keysFoundInTemplate = new HashSet<>();
        this.originalText = originalText;
        estimatedSize = (int) Math.round(originalText.length() * SIZE_ESTIMATE_MODIFIER);
    }

    /**
     * Given a map of key names -> value, render a template.
     */
    public String renderTemplate(Map<String, String> myMap, boolean runWithChecks) {
        return internalRender(runWithChecks, List.of(myMap)).toString();
    }

    /**
     * Given a list of maps of key names -> value, render a template
     * multiple times.
     */
    public String renderTemplate(List<Map<String, String>> myMap, boolean runWithChecks) {
        return internalRender(runWithChecks, myMap).toString();
    }

    /**
     * Given a map of key names -> value, render a template.
     */
    public String renderTemplate(Map<String, String> myMap) {
        return internalRender(true, List.of(myMap)).toString();
    }

    /**
     * Given a list of maps of key names -> value, render a template
     * multiple times.
     */
    public String renderTemplate(List<Map<String, String>> myMap) {
        return internalRender(true, myMap).toString();
    }

    /**
     * Render the template without any data
     */
    public String renderTemplate() {
        return renderTemplate(Map.of());
    }

    /**
     * Builds a {@link TemplateProcessor} from a string
     * containing a proper template.  Templated values
     * are surrounded by double-curly-braces, i.e. {{foo}} or {{ foo }}
     */
    public static TemplateProcessor buildProcessor(String template) {
        if (template == null || template.isEmpty()) {
            throw new TemplateRenderException("The input to building a template must be a non-empty string");
        }
        List<TemplateSection> tSections = renderToTemplateSections(template);
        var tp = new TemplateProcessor(tSections, template);
        Set<String> keysFound = tSections.stream()
                .filter(x -> x.templateType.equals(TemplateType.DYNAMIC_TEXT))
                .map(x -> x.key)
                .collect(Collectors.toSet());
        tp.keysFoundInTemplate.addAll(keysFound);

        return tp;
    }

    private static List<TemplateSection> renderToTemplateSections(String template) {
        // this value holds the entire template after processing, comprised
        // of an ordered list of TemplateSections
        var tSections = new ArrayList<TemplateSection>();

        // these values are used for logging and setting proper indentation
        int rowNumber = 1;
        int columnNumber = 1;
        // this value records the indent of the beginning of template keys,
        // so we can properly indent the values later.
        int indentation = 0;

        boolean lineStartWithWhitespace = false;

        StringBuilder builder = new StringBuilder();
        // this flag is to help us understand whether we are currently reading the
        // name of a template literal.
        // e.g. in the case of hello {{ name }}, "name" is the literal.
        boolean isInsideTemplateKeyLiteral = false;
        for (int i = 0; i < template.length(); i++) {
            char charAtCursor = template.charAt(i);

            if (charAtCursor == '{' && (i + 1) < template.length() && template.charAt(i + 1) == '{') {
                isInsideTemplateKeyLiteral = true;
                indentation = lineStartWithWhitespace ? columnNumber - 1 : 0;
                i += 1;
                builder = processSectionInside(builder, tSections);
            } else if (isInsideTemplateKeyLiteral && charAtCursor == '}' && (i + 1) < template.length() && template.charAt(i + 1) == '}') {
                isInsideTemplateKeyLiteral = false;
                i += 1;
                builder = processSectionOutside(builder, tSections, indentation);
                indentation = 0;
            } else {
                builder.append(charAtCursor);

                /*
                 if we're at the end of the template, it's our last chance to
                 add a substring (we can't be adding to a key, since if we're
                 at the end, and it's not a closing brace, it's a malformed
                 template.
                 */
                if (i == template.length() - 1) {
                    if (isInsideTemplateKeyLiteral) {
                        // if we're exiting this string while inside a template literal, then
                        // we're reading a corrupted input, and we should make that clear
                        // to our caller.
                        String templateSample = template.length() > 10 ? template.substring(0, 10) + "..." : template;
                        throw new TemplateParseException(
                                "parsing failed for string starting with \"" + templateSample + "\" at line " + rowNumber + " and column " + columnNumber);
                    }
                    tSections.add(new TemplateSection(null, builder.toString(), TemplateType.STATIC_TEXT, 0));
                }
            }

            if (charAtCursor == '\n') {
                rowNumber += 1;
                columnNumber = 1;
                lineStartWithWhitespace = true;
            } else {
                columnNumber += 1;
                if (!Character.isWhitespace(charAtCursor)) {
                    lineStartWithWhitespace = false;
                }
            }

        }
        return tSections;
    }

    private static StringBuilder processSectionInside(StringBuilder builder, List<TemplateSection> tSections) {
        if (!builder.isEmpty()) {
            tSections.add(new TemplateSection(null, builder.toString(), TemplateType.STATIC_TEXT, 0));
            builder = new StringBuilder();
        }
        return builder;
    }

    private static StringBuilder processSectionOutside(StringBuilder builder,
                                               List<TemplateSection> tSections,
                                               int indent) {
        String key = builder.toString().trim();
        tSections.add(new TemplateSection(key, "", TemplateType.DYNAMIC_TEXT, indent));
        builder = new StringBuilder();
        return builder;
    }

    /**
     * Returns the original unchanged template string
     */
    public String getOriginalText() {
        return originalText;
    }

    /**
     * now, loop through the lists of data we were given, with the
     * internal template sections in hand
     */
    private StringBuilder internalRender(boolean runWithChecks, List<Map<String, String>> dataList) {
        if (runWithChecks) {
            correctnessCheck(dataList);
        }
        int capacity = calculateEstimatedSize(dataList);
        StringBuilder parts = new StringBuilder(capacity);
        return internalRender(parts, dataList);
    }

    /**
     * This examines the currently registered data lists and template keys
     * and confirms they are aligned.  It will throw an exception if they
     * are not perfectly correlated.
     * <br>
     *
     */
    private void correctnessCheck(List<Map<String, String>> dataList) {
        if (dataList.isEmpty()) {
            if (!keysFoundInTemplate.isEmpty()) {
                throw new TemplateRenderException("No data was provided for these keys: " + keysFoundInTemplate);
            }
        } else {
            // check for inconsistencies between maps in the data list
            Set<String> keysInFirstMap = dataList.getFirst().keySet();
            for (Map<String, String> data : dataList) {
                if (!data.keySet().equals(keysInFirstMap)) {
                    Set<String> result = differenceBetweenSets(data.keySet(), keysInFirstMap);
                    throw new TemplateRenderException("In registered data, the maps were inconsistent on these keys: " + result);
                }
            }

            // ensure consistency between the registered data and the template keys
            HashSet<String> copyOfTemplateKeys = new HashSet<>(keysFoundInTemplate);
            copyOfTemplateKeys.removeAll(keysInFirstMap);
            if (!copyOfTemplateKeys.isEmpty()) {
                throw new TemplateRenderException("These keys in the template were not provided data: " + copyOfTemplateKeys);
            }

            HashSet<String> copyOfDataKeys = new HashSet<>(keysInFirstMap);
            copyOfDataKeys.removeAll(keysFoundInTemplate);
            if (!copyOfDataKeys.isEmpty()) {
                throw new TemplateRenderException("These keys in the data did not match anything in the template: " + copyOfDataKeys);
            }
        }
    }

    private static Set<String> differenceBetweenSets(Set<String> set1, Set<String> set2) {
        Set<String> union = new HashSet<>(set2);
        union.addAll(set1);
        Set<String> intersection = new HashSet<>(set2);
        intersection.retainAll(set1);

        Set<String> result = new HashSet<>(union);
        result.removeAll(intersection);
        return result;
    }

    /**
     * build up a calculated size estimate for this and all
     * nested templates.
     */
    private int calculateEstimatedSize(List<Map<String, String>> dataList) {
        // the size of the datalist specifies how many times we will render ourselves.
        int sizeMultiplier = dataList.isEmpty() ? 1 : dataList.size();
        return sizeMultiplier * estimatedSize;
    }

    private StringBuilder internalRender(StringBuilder parts, List<Map<String, String>> dataList) {
        if (dataList.isEmpty()) {
            return parts;
        }

        // build ourself out for each map of data given
        for (int dataListIndex = 0, dataListSize = dataList.size(); dataListIndex < dataListSize; dataListIndex++) {
            if (dataListIndex > 0) {
                parts.append("\n");
            }
            Map<String, String> myDataMap = dataList.get(dataListIndex);

            for (TemplateSection templateSection : templateSections) {
                if (templateSection.templateType == TemplateType.STATIC_TEXT) {
                    parts.append(templateSection.staticData);
                } else {
                    String value = myDataMap.getOrDefault(templateSection.key, "");
                    if (templateSection.indent > 0) {
                        value = value.replace("\n", "\n" + " ".repeat(templateSection.indent));
                    }
                    parts.append(value);
                }
            }
        }
        return parts;
    }
}


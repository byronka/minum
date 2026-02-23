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
     * The template is made up of a list of these, which are the static (unchanging)
     * parts, along with the dynamic parts the user fills in later.
     */
    private final List<TemplateSection> templateSections;

    /**
     * This value is used to calculate a quick estimate of how many
     * bytes of memory we will need for the buffer holding our generated string
     */
    private final static double SIZE_ESTIMATE_MODIFIER = 1.1;

    /**
     * The String value given to us by the developer when creating this template
     */
    private final String originalText;

    /**
     * A general estimate of the resulting size of the output from the
     * template process, for one rendering of the template, to provide
     * quick information when building a buffer to contain the results
     * which is appropriately sized, which greatly improves performance
     */
    private final int estimatedSizeOfSingleTemplate;

    /**
     * Used when checking correctness - we check that the user is providing
     * key->value pairs that match the expected template keys.
     */
    private final Set<String> keysFoundInTemplate;


    /**
     * Instantiate a new object with a list of {@link TemplateSection}.
     */
    private TemplateProcessor(List<TemplateSection> templateSections, String originalText) {
        this.templateSections = templateSections;
        this.originalText = originalText;
        this.estimatedSizeOfSingleTemplate = (int) (originalText.length() * SIZE_ESTIMATE_MODIFIER);
        keysFoundInTemplate = templateSections.stream()
                .filter(x -> x.templateType.equals(TemplateType.DYNAMIC_TEXT))
                .map(x -> x.key)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Given a map of key names -> value, render a template.
     */
    public String renderTemplate(Map<String, String> myMap) {
        return renderTemplate(List.of(myMap), "");
    }

    /**
     * Given a list, map of key names -> value, render one template
     * for each, joined by a newline.  Use {@link #renderTemplate(List, String)}
     * for control over the delimiter.
     */
    public String renderTemplate(List<Map<String, String>> data) {
        return renderTemplate(data, "\n");
    }

    /**
     * Render a list of maps.
     * <p>
     *     Similar to {@link #renderTemplate(Map)} but takes a list of maps instead
     *     of just one.  The "delimiter" argument is inserted between each rendered
     *     template.
     * </p>
     */
    public String renderTemplate(List<Map<String, String>> data, String delimiter) {
        correctnessCheck(data);

        // build an appropriately-sized buffer for output
        int capacity = estimatedSizeOfSingleTemplate * data.size();
        StringBuilder stringBuilder = new StringBuilder(capacity);

        for (int i = 0; i < data.size(); i++) {
            for (TemplateSection templateSection : templateSections) {
                templateSection.render(data.get(i), stringBuilder);
            }
            if (i != data.size() - 1) {
                stringBuilder.append(delimiter);
            }
        }
        return stringBuilder.toString();
    }


    /**
     * Builds a {@link TemplateProcessor} from a string
     * containing a proper template.  Templated values
     * are surrounded by double-curly-braces, i.e. {{foo}} or {{ foo }}
     */
    public static TemplateProcessor buildProcessor(String template) {
        // this value holds the entire template after processing, comprised
        // of an ordered list of TemplateSections
        var tSections = new ArrayList<TemplateSection>();

        // these values are used for logging and setting proper indentation
        int rowNumber = 1;
        int columnNumber = 1;

        // this value records the indent of the beginning of template keys,
        // so we can properly indent the values later.
        int startOfKey = 0;

        StringBuilder builder = new StringBuilder();

        // this flag is to help us understand whether we are currently reading the
        // name of a template literal.
        // e.g. in the case of hello {{ name }}, "name" is the literal.
        boolean isInsideTemplateKeyLiteral = false;
        for (int i = 0; i < template.length(); i++) {
            char charAtCursor = template.charAt(i);

            if (justArrivedInside(template, charAtCursor, i)) {
                isInsideTemplateKeyLiteral = true;
                startOfKey = columnNumber;
                i += 1;
                builder = processSectionInside(builder, tSections);
            } else if (justArrivedOutside(template, charAtCursor, i, isInsideTemplateKeyLiteral)) {
                isInsideTemplateKeyLiteral = false;
                i += 1;
                builder = processSectionOutside(builder, tSections, startOfKey);
                startOfKey = 0;
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
                    tSections.add(new TemplateSection(null, builder.toString(), 0, TemplateType.STATIC_TEXT));
                }
            }

            if (charAtCursor == '\n') {
                rowNumber += 1;
                columnNumber = 1;
            } else {
                columnNumber += 1;
            }

        }

        return new TemplateProcessor(tSections, template);
    }

    /**
     * Returns the raw template string provided at creation.
     */
    public String getOriginalText() {
        return this.originalText;
    }

    static StringBuilder processSectionInside(StringBuilder builder, List<TemplateSection> tSections) {
        if (!builder.isEmpty()) {
            tSections.add(new TemplateSection(null, builder.toString(), 0, TemplateType.STATIC_TEXT));
            builder = new StringBuilder();
        }
        return builder;
    }

    static StringBuilder processSectionOutside(StringBuilder builder, List<TemplateSection> tSections, int indent) {
        if (!builder.isEmpty()) {
            String trimmedKey = builder.toString().trim();
            tSections.add(new TemplateSection(trimmedKey, null, indent, TemplateType.DYNAMIC_TEXT));
            builder = new StringBuilder();
        }
        return builder;
    }

    /**
     * Just left a template key value.
     * <pre>
     *     hello {{ world }}
     *                ^
     *                +------Template key
     *
     * </pre>
     */
    static boolean justArrivedOutside(String template, char charAtCursor, int i, boolean isInsideTemplateKeyLiteral) {
        return charAtCursor == '}' && (i + 1) < template.length() && template.charAt(i + 1) == '}' && isInsideTemplateKeyLiteral;
    }

    /**
     * Just arrived inside a template key value.
     * <pre>
     *     hello {{ world }}
     *                ^
     *                +------Template key
     *
     * </pre>
     */
    static boolean justArrivedInside(String template, char charAtCursor, int i) {
        return charAtCursor == '{' && (i + 1) < template.length() && template.charAt(i + 1) == '{';
    }

    /**
     * This examines the currently registered data lists and template keys
     * and confirms they are aligned.  It will throw an exception if they
     * are not perfectly correlated.
     * <br>
     *
     */
    private void correctnessCheck(List<Map<String, String>> dataList) {
        HashSet<String> copyOfKeysInTemplate = new HashSet<>(keysFoundInTemplate);

        // check for inconsistencies between maps in the data list
        Set<String> keysInFirstMap = dataList.getFirst().keySet();

        for (Map<String, String> data : dataList) {
            if (!data.keySet().equals(keysInFirstMap)) {
                Set<String> result = differenceBetweenSets(data.keySet(), keysInFirstMap);
                throw new TemplateRenderException("In registered data, the maps were inconsistent on these keys: " + result);
            }
        }

        // ensure consistency between the registered data and the template keys
        HashSet<String> copyOfTemplateKeys = new HashSet<>(copyOfKeysInTemplate);
        copyOfTemplateKeys.removeAll(keysInFirstMap);
        if (!copyOfTemplateKeys.isEmpty()) {
            throw new TemplateRenderException("These keys in the template were not provided data: " + copyOfTemplateKeys);
        }

        HashSet<String> copyOfDataKeys = new HashSet<>(keysInFirstMap);
        copyOfDataKeys.removeAll(copyOfKeysInTemplate);
        if (!copyOfDataKeys.isEmpty()) {
            throw new TemplateRenderException("These keys in the data did not match anything in the template: " + copyOfDataKeys);
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

}


package com.renomad.minum.templating;


import java.util.*;

/**
 * Allows rendering of templates
 *
 * <p>
 * In order to perform speedy template rendering, it is necessary
 * to get the template converted into this class.  The way to do
 * that is as follows.
 * </p>
 * <p>
 * First, you will be started with some suitable template. The values
 * to be substituted are surrounded by double brackets.  Here's an example:
 * </p>
 * <pre>
 * Hello, my name is {{name}}
 * </pre>
 * <p>
 * Then, feed that string into {@link #buildProcessor}, like
 * this:
 * </p>
 * <pre>
 * <code>
 * {@code String input = "Hello, my name is {{name}}"
 * var helloProcessor = TemplateProcessor.buildProcessor(input);}
 * </code>
 * </pre>
 * <p>
 * Now that you have built a template processor, hold onto it! The generation of
 * the template processor is the costly activity.  After that, you can use it
 * efficiently, by feeding it a {@link Map} of key names to desired values. For
 * our example, maybe we want <em>name</em> to be replaced with <em>Susanne</em>.
 * In that case, we would do this:
 * </p>
 * <pre>
 * <code>
 * {@code var myMap = Map.of("name", "Susanne");
 * String fullyRenderedString = helloProcessor.renderTemplate(myMap);}
 * </code>
 * </pre>
 * <p>
 *     The result is: Hello, my name is Susanne
 * </p>
 */
public final class TemplateProcessor {

    private final List<TemplateSection> templateSections;

    /**
     * Instantiate a new object with a list of {@link TemplateSection}.
     */
    private TemplateProcessor(List<TemplateSection> templateSections) {
        this.templateSections = templateSections;
    }

    /**
     * Given a map of key names -> value, render a template.
     */
    public String renderTemplate(Map<String, String> myMap) {
        // This indicates the count of usages of each key
        Map <String, Integer> usageMap = new HashMap<>();
        StringBuilder builder = new StringBuilder();
        for (TemplateSection templateSection : templateSections) {
            RenderingResult result = templateSection.render(myMap);
            if (result.renderedSection().isEmpty() &&
                    templateSection.isOptional &&
                    !builder.isEmpty())
                // Remove last character if possible, prevents spaces where there shouldn't be
                builder.replace(builder.length() - 1, builder.length(), "");
            builder.append(result.renderedSection());
            String appliedKey = result.appliedKey();
            if (appliedKey != null) {
                usageMap.merge(appliedKey, 1, Integer::sum);
            }
        }
        Set<String> unusedKeys = new HashSet<>(myMap.keySet());
        unusedKeys.removeIf(usageMap.keySet()::contains);

        if (!unusedKeys.isEmpty()) {
            throw new TemplateRenderException("No corresponding key in template found for these keys: " + String.join(", ", unusedKeys));
        }
        
        return builder.toString();
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
        boolean isMarkedOptional = false;
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
                builder = processSectionOutside(builder, tSections, startOfKey, isMarkedOptional);
                isMarkedOptional = false;
                startOfKey = 0;
            } else {
                if (isInsideTemplateKeyLiteral &&
                        charAtCursor == '?' &&
                        (template.charAt(i + 1) == '}' || template.charAt(i + 2) == '}')) {
                    isMarkedOptional = true;
                    continue; // Don't add this to the key literal
                }

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
                    tSections.add(new TemplateSection(null, builder.toString(), 0, isMarkedOptional));
                }
            }

            if (charAtCursor == '\n') {
                rowNumber += 1;
                columnNumber = 1;
            } else {
                columnNumber += 1;
            }

        }

        return new TemplateProcessor(tSections);
    }

    static StringBuilder processSectionInside(StringBuilder builder, List<TemplateSection> tSections) {
        if (!builder.isEmpty()) {
            tSections.add(new TemplateSection(null, builder.toString(), 0));
            builder = new StringBuilder();
        }
        return builder;
    }

    static StringBuilder processSectionOutside(StringBuilder builder, List<TemplateSection> tSections, int indent) {
        return processSectionOutside(builder, tSections, indent, false);
    }

    static StringBuilder processSectionOutside(StringBuilder builder, List<TemplateSection> tSections, int indent, boolean isMarkedOptional) {
        if (!builder.isEmpty()) {
            tSections.add(new TemplateSection(builder.toString().trim(), null, indent, isMarkedOptional));
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
}


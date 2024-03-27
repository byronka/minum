package com.renomad.minum.templating;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        StringBuilder sb = new StringBuilder();
        for (TemplateSection templateSection : templateSections) {
            sb.append(templateSection.render(myMap));
        }
        return sb.toString();
    }

    /**
     * Builds a {@link TemplateProcessor} from a string
     * containing a proper template.  Templated values
     * are surrounded by double-curly-braces, i.e. {{foo}} or {{ foo }}
     */
    public static TemplateProcessor buildProcessor(String template) {
        var tSections = new ArrayList<TemplateSection>();
        StringBuilder builder = new StringBuilder();
        // this flag is to help us understand whether we are currently reading the
        // name of a template literal.
        // e.g. in the case of hello {{ name }}, "name" is the literal.
        boolean isInsideTemplateKeyLiteral = false;
        for (int i = 0; i < template.length(); i++) {

            char charAtCursor = template.charAt(i);

            if (justArrivedInside(template, charAtCursor, i)) {
                isInsideTemplateKeyLiteral = true;
                i += 1;
                builder = processSectionInside(builder, tSections);
            } else if (justArrivedOutside(template, charAtCursor, i, isInsideTemplateKeyLiteral)) {
                isInsideTemplateKeyLiteral = false;
                i += 1;
                builder = processSectionOutside(builder, tSections);
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
                        String templateSample = template.length() > 10 ? template.substring(0, 10) : template;
                        throw new TemplateParseException("parsing failed for string starting with " + templateSample);
                    }
                    tSections.add(new TemplateSection(null, builder.toString()));
                }
            }
        }

        return new TemplateProcessor(tSections);
    }

    static StringBuilder processSectionInside(StringBuilder builder, ArrayList<TemplateSection> tSections) {
        if (!builder.isEmpty()) {
            tSections.add(new TemplateSection(null, builder.toString()));
            builder = new StringBuilder();
        }
        return builder;
    }

    static StringBuilder processSectionOutside(StringBuilder builder, ArrayList<TemplateSection> tSections) {
        if (!builder.isEmpty()) {
            tSections.add(new TemplateSection(builder.toString().trim(), null));
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


package minum.templating;


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

    final private List<TemplateSection> templateSections;

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
     * are surrounded by double-curly-braces, i.e. {{foo}}
     */
    public static TemplateProcessor buildProcessor(String template) {
        var tSections = new ArrayList<TemplateSection>();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < template.length(); i++) {

            char charAtCursor = template.charAt(i);

            if (charAtCursor == '{' && (i + 1) < template.length() && template.charAt(i + 1) == '{') {
                i += 1;
                if (builder.length() > 0) {
                    tSections.add(new TemplateSection(null, builder.toString()));
                    builder = new StringBuilder();
                }
                continue;
            }

            if (charAtCursor == '}' && (i + 1) < template.length() && template.charAt(i + 1) == '}') {
                i += 1;
                if (builder.length() > 0) {
                    tSections.add(new TemplateSection(builder.toString(), null));
                    builder = new StringBuilder();
                }
                continue;
            }

            builder.append(charAtCursor);

            /*
             if we're at the end of the template, it's our last chance to
             add a substring (we can't be adding to a key, since if we're
             at the end, and it's not a closing brace, it's a malformed
             template.
             */
            if (i == template.length() - 1) {
                tSections.add(new TemplateSection(null, builder.toString()));
            }
        }

        return new TemplateProcessor(tSections);
    }
}


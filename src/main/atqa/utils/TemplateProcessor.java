package atqa.utils;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TemplateProcessor {

    List<TemplateSection> templateSections;

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


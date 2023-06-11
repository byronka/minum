package atqa.utils;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TemplateProcessor {

    List<TemplateSection> templateSections;

    public TemplateProcessor(List<TemplateSection> templateSections) {
        this.templateSections = templateSections;
    }

    public String renderTemplate(Map<String, String> myMap) {
        StringBuilder sb = new StringBuilder();
        for (TemplateSection templateSection : templateSections) {
            sb.append(templateSection.render(myMap));
        }
        return sb.toString();
    }

    /**
     * This parser gets to stay simpler, because the rules are
     * just this: if we encounter an opening curly brace, we're
     * now inside a key.  Once we hit a closing brace, we're done
     * and back into reading substrings.
     */
    public static TemplateProcessor makeTemplateList(String template) {
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


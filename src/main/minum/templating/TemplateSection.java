package minum.templating;

import java.util.Map;

public record TemplateSection(String key, String subString) {

    public String render(Map<String, String> myMap) {
        if (subString != null) {
            return subString;
        } else if (key != null) {
            var value = myMap.get(key);
            if (value == null) throw new TemplateRenderException("Missing a value for key {"+key+"}");
            return value;
        } else {
            return "";
        }
    }

}
package minum.templating;

import java.util.Map;

import static minum.utils.Invariants.mustBeTrue;

/**
 * Represents one item in the list that will eventually be cooked
 * up into a fully-rendered string.  This record is the magic
 * ingredient to an easy templating system.  It's got two fields,
 * key and substring, and .  If it has a key, then this object will be getting
 * replaced during final string rendering.  If it has a substring,
 * then the substring gets concatenated unchanged when the final string
 * is rendered.
 */
public record TemplateSection(String key, String subString) {

    public String render(Map<String, String> myMap) {
        mustBeTrue(subString != null || key != null, "Either the key or substring must exist");
        if (subString != null) {
            mustBeTrue(key == null, "If this object has a substring, then it must not have a key");
            return subString;
        } else {
            mustBeTrue(subString == null, "If this object has a key, then it must not have a substring");
            var value = myMap.get(key);
            if (value == null) throw new TemplateRenderException("Missing a value for key {"+key+"}");
            return value;
        }
    }

}
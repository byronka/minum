package com.renomad.minum.templating;

import java.util.List;
import java.util.Map;

import static com.renomad.minum.utils.Invariants.mustBeTrue;
import static com.renomad.minum.utils.SerializationUtils.tokenizer;

/**
 * Represents one item in the list that will eventually be cooked
 * up into a fully-rendered string.  This record is the magic
 * ingredient to an easy templating system. If it has a key,
 * then this object will be getting replaced during final string rendering.  If it has a substring,
 * then the substring gets concatenated unchanged when the final string
 * is rendered.
 * @param indent the column number, measured from the left, of the first character of this template key.  This is used
 *               to indent the subsequent lines of text when there are newlines in the replacement
 *               text. For example, if the indent is 5, and the value is "a", then it should indent like this:
 *               <br>
 *               <pre>{@code
 *               12345
 *                   a
 *               }</pre>
 * @param key the name of the key, e.g. "name", or "color"
 * @param subString the template content around the keys.  For example, in the text
 *                  of "my favorite color is {{ color }} and I like it",
 *                  it would generate three template sections - "my favorite color is" would be
 *                  the first subString, then a key of "color", then a third subString of "and I like it"
 */
record TemplateSection(String key, String subString, int indent) {

    public String render(Map<String, String> myMap) {
        mustBeTrue(subString != null || key != null, "Either the key or substring must exist");
        if (subString != null) {
            mustBeTrue(key == null, "If this object has a substring, then it must not have a key");
            return subString;
        } else {
            String value = myMap.get(key);
            if (value == null) throw new TemplateRenderException("Missing a value for key {"+key+"}");
            List<String> lines = tokenizer(value, '\n', 10_000);

            // if, after splitting on newlines, we have more than one line, we'll indent the remaining
            // lines so that they end up at the same column as the first line.
            var stringBuilder = new StringBuilder(lines.getFirst());
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isEmpty()) {
                    stringBuilder.append('\n');
                } else {
                    stringBuilder.append('\n').append(" ".repeat(indent - 1)).append(lines.get(i));
                }
            }
            return stringBuilder.toString();
        }
    }

}
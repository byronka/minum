package com.renomad.minum.templating;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.renomad.minum.templating.TemplateType.DYNAMIC_TEXT;
import static com.renomad.minum.templating.TemplateType.STATIC_TEXT;
import static com.renomad.minum.utils.Invariants.mustBeTrue;
import static com.renomad.minum.utils.SerializationUtils.tokenizer;

/**
 * Represents one item in the list that will eventually be cooked
 * up into a fully-rendered string.  This record is the magic
 * ingredient to an easy templating system. If it has a key,
 * then this object will be getting replaced during final string rendering.  If it has a substring,
 * then the substring gets concatenated unchanged when the final string
 * is rendered.
 */
final class TemplateSection {

    final String key;
    private final String subString;
    private final int indent;
    public final TemplateType templateType;


    /**
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
    public TemplateSection(String key, String subString, int indent, TemplateType templateType) {
        this.key = key;
        this.subString = subString;
        this.indent = indent;
        this.templateType = templateType;
        if ((templateType.equals(STATIC_TEXT) && (key != null || subString == null)) ||
            (templateType.equals(DYNAMIC_TEXT) && (key == null || subString != null))) {
            throw new TemplateRenderException("Invalid templateSection: " + this);
        }
    }

    /**
     * It would be absurd to send a million lines to a browser, much
     * less ten million.  This is here to set an upper limit on
     * the tokenizer loop, to prevent certain attacks.
     */
    static final int MAXIMUM_LINES_ALLOWED = 10_000_000;

    public void render(Map<String, String> myMap, StringBuilder stringBuilder) {
        if (templateType.equals(STATIC_TEXT)) {
            stringBuilder.append(subString);
        } else {
            String value = myMap.get(key);
            if (value == null) throw new TemplateRenderException("Missing a value for key {"+key+"}");
            List<String> lines = tokenizer(value, '\n', MAXIMUM_LINES_ALLOWED);

            // if, after splitting on newlines, we have more than one line, we'll indent the remaining
            // lines so that they end up at the same column as the first line.
            stringBuilder.append(lines.getFirst());
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isEmpty()) {
                    stringBuilder.append('\n');
                } else {
                    stringBuilder.append('\n').append(" ".repeat(indent - 1)).append(lines.get(i));
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TemplateSection that = (TemplateSection) o;
        return indent == that.indent && Objects.equals(key, that.key) && Objects.equals(subString, that.subString) && templateType == that.templateType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, subString, indent, templateType);
    }

    @Override
    public String toString() {
        return "TemplateSection{" +
                "key='" + key + '\'' +
                ", subString='" + subString + '\'' +
                ", indent=" + indent +
                ", templateType=" + templateType +
                '}';
    }
}
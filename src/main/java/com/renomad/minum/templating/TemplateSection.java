package com.renomad.minum.templating;

/**
 * Represents one item in the list that will eventually be cooked
 * up into a fully-rendered string.  This record is the magic
 * ingredient to an easy templating system. If it has a key,
 * then this object will be getting replaced during final string rendering.  If it has a substring,
 * then the substring gets concatenated unchanged when the final string
 * is rendered.
 */
class TemplateSection {

    public final String key;
    public final int indent;
    public final String staticData;
    public final TemplateType templateType;
    public final TemplateProcessor templateProcessor;

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
    public TemplateSection(String key, String subString, TemplateProcessor templateProcessor, TemplateType templateType, int indent) {
        this.key = key;
        this.staticData = subString;
        this.templateProcessor = templateProcessor;
        this.templateType = templateType;
        this.indent = indent;
    }

}
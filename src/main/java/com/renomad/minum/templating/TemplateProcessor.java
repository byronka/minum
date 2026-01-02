package com.renomad.minum.templating;

import java.util.*;
import java.util.stream.Collectors;

import static com.renomad.minum.utils.SerializationUtils.tokenizer;

/**
 * This class provides methods for working with templates.
 * <p>
 * The first step is to write a template.  Here is an example:
 * </p>
 * <pre>
 * Hello, my name is {{name}}
 * </pre>
 * <p>
 * Then, feed that string into the {@link #buildProcessor} method, like
 * this:
 * </p>
 * <pre>
 * {@code
 *   String input = "Hello, my name is {{name}}"
 *   TemplateProcessor helloProcessor = TemplateProcessor.buildProcessor(input);
 * }
 * </pre>
 * <p>
 * The returned value ("helloProcessor") can be rendered with different values. For
 * example:
 * </p>
 * <pre>
 * {@code
 *   Map<String,String> myMap = Map.of("name", "Susanne");
 *   String fullyRenderedString = helloProcessor.renderTemplate(myMap);
 * }
 * </pre>
 * <p>
 *     The result is:
 * </p>
 * <pre>
 *     {@code Hello, my name is Susanne}
 * </pre>
 */
public final class TemplateProcessor {

    /**
     * template sections by indentation
     */
    private Map<Integer, List<TemplateSection>> templatesSectionsByIndent;
    private List<Map<String, String>> defaultDataList = new ArrayList<>();
    private final Set<String> keysFoundInTemplate;
    private final Set<String> keysRegisteredForInnerTemplates;
    private final String originalText;
    /**
     * This value is used to calculate a quick estimate of how many
     * bytes of memory we will need for the buffer holding our generated string
     */
    private final static double SIZE_ESTIMATE_MODIFIER = 1.1;
    private final int estimatedSize;
    private Map<String, TemplateProcessor> innerTemplates;

    /**
     * Instantiate a new object with a list of {@link TemplateSection}.
     */
    private TemplateProcessor(List<TemplateSection> templateSections, String originalText) {
        this.templatesSectionsByIndent = new HashMap<>();
        this.templatesSectionsByIndent.put(0, templateSections);
        keysFoundInTemplate = new HashSet<>();
        keysRegisteredForInnerTemplates = new HashSet<>();
        this.originalText = originalText;
        this.innerTemplates = new HashMap<>();
        estimatedSize = (int) Math.round(originalText.length() * SIZE_ESTIMATE_MODIFIER);
    }

    /**
     * Given a map of key names -> value, render a template.
     */
    public String renderTemplate(Map<String, String> myMap) {
        return internalRender(true, List.of(myMap)).toString();
    }

    /**
     * Given a list of maps of key names -> value, render a template
     * multiple times.
     */
    public String renderTemplate(List<Map<String, String>> myMap) {
        return internalRender(true, myMap).toString();
    }

    /**
     * Recursively assembles the template and sub-templates
     */
    public String renderTemplate() {
        return internalRender(true, defaultDataList).toString();
    }

    /**
     * Render the template and any nested sub-templates.  All templates
     * must have data registered before running this method.
     * @param runWithChecks Default: true.  Check that there is a 1-to-1 correspondence between
     *                      the keys provided and keys in the template and sub-templates, throwing
     *                      an exception if there are any errors. Also check that the maps
     *                      of data are consistent.  This should be set true unless there is a reason
     *                      to aim for maximum performance, which is actually not
     *                      valuable in most cases, since the bottleneck is the business algorithms, database,
     *                      and HTTP processing.
     */
    public String renderTemplate(boolean runWithChecks) {
        return internalRender(runWithChecks, defaultDataList).toString();
    }

    /**
     * Assign data.  Keys must match to template.
     */
    public void registerData(List<Map<String, String>> dataList) {
        if (dataList == null){
            throw new TemplateRenderException("provided data cannot be null");
        } else if (dataList.isEmpty()) {
            throw new TemplateRenderException("No data provided in registerData call");
        }

        this.defaultDataList = dataList;
    }

    /**
     * Builds a {@link TemplateProcessor} from a string
     * containing a proper template.  Templated values
     * are surrounded by double-curly-braces, i.e. {{foo}} or {{ foo }}
     */
    public static TemplateProcessor buildProcessor(String template) {
        if (template == null || template.isEmpty()) {
            throw new TemplateRenderException("The input to building a template must be a non-empty string");
        }
        var tp = new TemplateProcessor(new ArrayList<>(), template);
        List<TemplateSection> tSections = tp.renderToTemplateSections(template);
        Set<String> keysFound = tSections.stream()
                .filter(x -> x.templateType.equals(TemplateType.DYNAMIC_TEXT))
                .map(x -> x.key)
                .collect(Collectors.toSet());
        tp.keysFoundInTemplate.addAll(keysFound);
        tp.templatesSectionsByIndent.put(0, tSections);

        return tp;
    }

    private List<TemplateSection> renderToTemplateSections(String template) {
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
        for (int i = 0; i < template.length(); i++) {
            char charAtCursor = template.charAt(i);

            if (charAtCursor == '{' && (i + 1) < template.length() && template.charAt(i + 1) == '{') {
                isInsideTemplateKeyLiteral = true;
                startOfKey = columnNumber - 1;
                i += 1;
                builder = processSectionInside(builder, tSections);
            } else if (isInsideTemplateKeyLiteral && charAtCursor == '}' && (i + 1) < template.length() && template.charAt(i + 1) == '}') {
                isInsideTemplateKeyLiteral = false;
                i += 1;
                builder = processSectionOutside(builder, tSections, startOfKey);
                startOfKey = 0;
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
                        String templateSample = template.length() > 10 ? template.substring(0, 10) + "..." : template;
                        throw new TemplateParseException(
                                "parsing failed for string starting with \"" + templateSample + "\" at line " + rowNumber + " and column " + columnNumber);
                    }
                    tSections.add(new TemplateSection(null, builder.toString(), null, TemplateType.STATIC_TEXT, 0));
                }
            }

            if (charAtCursor == '\n') {
                rowNumber += 1;
                columnNumber = 1;
            } else {
                columnNumber += 1;
            }

        }
        return tSections;
    }

    private static StringBuilder processSectionInside(StringBuilder builder, List<TemplateSection> tSections) {
        if (!builder.isEmpty()) {
            tSections.add(new TemplateSection(null, builder.toString(), null, TemplateType.STATIC_TEXT, 0));
            builder = new StringBuilder();
        }
        return builder;
    }

    private static StringBuilder processSectionOutside(StringBuilder builder,
                                               List<TemplateSection> tSections,
                                               int indent) {
        String key = builder.toString().trim();
        tSections.add(new TemplateSection(key, "", null, TemplateType.DYNAMIC_TEXT, indent));
        builder = new StringBuilder();
        return builder;
    }

    /**
     * Binds an inner template to a key of this template.
     */
    public TemplateProcessor registerInnerTemplate(String key, TemplateProcessor innerTemplate) {
        if (key == null || key.isBlank()) {
            throw new TemplateRenderException("The key must be a valid non-blank string");
        }
        if (innerTemplate == null) {
            throw new TemplateRenderException("The template must not be null");
        }
        if (this.equals(innerTemplate)) {
            throw new TemplateRenderException("Disallowed to register a template to itself as an inner template");
        }
        if (keysRegisteredForInnerTemplates.contains(key)) {
            throw new TemplateRenderException("key is already registered for use in another template: " + key);
        }

        // get the indent we should apply to each line after the first
        // by seeing what indent exists in the template sections and
        // creating a separate indented version for each one
        Set<Integer> necessaryIndentations = this.templatesSectionsByIndent.get(0).stream()
                .filter(x -> key.equals(x.key))
                .map(x -> x.indent).collect(Collectors.toSet());

        // make sure we have one for zero as well.
        necessaryIndentations.add(0);


        var copyOfInnerTemplate = new TemplateProcessor(innerTemplate.templatesSectionsByIndent.get(0), innerTemplate.getOriginalText());
        copyOfInnerTemplate.keysFoundInTemplate.addAll(innerTemplate.keysFoundInTemplate);
        copyOfInnerTemplate.keysRegisteredForInnerTemplates.addAll(innerTemplate.keysRegisteredForInnerTemplates);
        copyOfInnerTemplate.innerTemplates = new HashMap<>(innerTemplate.innerTemplates);
        this.innerTemplates.put(key, copyOfInnerTemplate);

        copyOfInnerTemplate.templatesSectionsByIndent.clear();

        // a non-configurable ceiling limit to avoid runaway loops
        int MAXIMUM_LINES_ALLOWED = 10_000_000;
        String originalText = copyOfInnerTemplate.getOriginalText();
        List<String> lines = tokenizer(originalText, '\n', MAXIMUM_LINES_ALLOWED);

        // if, after splitting on newlines, we have more than one line, we'll indent the remaining
        // lines so that they end up at the same column as the first line.
        for (int indentation : necessaryIndentations) {
            var indentedInnerTemplateText = new StringBuilder(lines.getFirst());
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isEmpty()) {
                    indentedInnerTemplateText.append('\n');
                } else {
                    indentedInnerTemplateText.append('\n').append(" ".repeat(indentation)).append(lines.get(i));
                }
            }
            List<TemplateSection> tSections = renderToTemplateSections(indentedInnerTemplateText.toString());
            copyOfInnerTemplate.templatesSectionsByIndent.put(indentation, tSections);

            // now, loop through all the template sections, replacing them appropriately with
            // new data labeled as INNER_TEMPLATE.
            Map<Integer, List<TemplateSection>> revisedTemplateSectionsByIndent = new HashMap<>();
            for (var templateSectionsByIndent : templatesSectionsByIndent.entrySet()) {
                List<TemplateSection> revisedList = new ArrayList<>();
                for (TemplateSection templateSection : templateSectionsByIndent.getValue()) {
                    if (key.equals(templateSection.key)) {
                        revisedList.add(new TemplateSection(templateSection.key,
                                templateSection.staticData,
                                copyOfInnerTemplate,
                                TemplateType.INNER_TEMPLATE,
                                templateSection.indent));
                    } else {
                        revisedList.add(templateSection);
                    }
                }
                revisedTemplateSectionsByIndent.put(templateSectionsByIndent.getKey(), revisedList);
            }
            templatesSectionsByIndent = revisedTemplateSectionsByIndent;

            this.keysRegisteredForInnerTemplates.add(key);
        }

        return copyOfInnerTemplate;
    }

    /**
     * Returns the original unchanged template string
     */
    public String getOriginalText() {
        return originalText;
    }

    /**
     * now, loop through the lists of data we were given, with the
     * internal template sections in hand
     */
    private StringBuilder internalRender(boolean runWithChecks, List<Map<String, String>> dataList) {
        if (runWithChecks) {
            correctnessCheck(dataList);
        }
        int capacity = calculateEstimatedSize(dataList);
        StringBuilder parts = new StringBuilder(capacity);
        return internalRender(0, parts, dataList);
    }

    /**
     * This examines the currently registered data lists and template keys
     * and confirms they are aligned.  It will throw an exception if they
     * are not perfectly correlated.
     * <br>
     *
     */
    private void correctnessCheck(List<Map<String, String>> dataList) {
        HashSet<String> copyOfKeysInTemplate = new HashSet<>(keysFoundInTemplate);
        copyOfKeysInTemplate.removeAll(this.innerTemplates.keySet());

        if (dataList.isEmpty()) {
            if (!copyOfKeysInTemplate.isEmpty()) {
                // at this point we know there is no data provided but the template
                // requires data, so throw an exception.

                throw new TemplateRenderException("No data was provided for these keys: " + copyOfKeysInTemplate);
            }
        } else {

            // check for inconsistencies between maps in the data list
            Set<String> keysInFirstMap = dataList.getFirst().keySet();
            for (Map<String, String> data : dataList) {
                if (!data.keySet().equals(keysInFirstMap)) {
                    Set<String> result = differenceBetweenSets(data.keySet(), keysInFirstMap);
                    throw new TemplateRenderException("In registered data, the maps were inconsistent on these keys: " + result);
                }
            }

            // ensure consistency between the registered data and the template keys
            HashSet<String> copyOfTemplateKeys = new HashSet<>(copyOfKeysInTemplate);
            copyOfTemplateKeys.removeAll(keysInFirstMap);
            if (!copyOfTemplateKeys.isEmpty()) {
                throw new TemplateRenderException("These keys in the template were not provided data: " + copyOfTemplateKeys);
            }

            HashSet<String> copyOfDataKeys = new HashSet<>(keysInFirstMap);
            copyOfDataKeys.removeAll(copyOfKeysInTemplate);
            if (!copyOfDataKeys.isEmpty()) {
                throw new TemplateRenderException("These keys in the data did not match anything in the template: " + copyOfDataKeys);
            }
        }

        for (TemplateProcessor tp : this.innerTemplates.values()) {
            tp.correctnessCheck(tp.defaultDataList); // TODO: Inner Map Support
        }

    }

    private static Set<String> differenceBetweenSets(Set<String> set1, Set<String> set2) {
        Set<String> union = new HashSet<>(set2);
        union.addAll(set1);
        Set<String> intersection = new HashSet<>(set2);
        intersection.retainAll(set1);

        Set<String> result = new HashSet<>(union);
        result.removeAll(intersection);
        return result;
    }

    /**
     * build up a calculated size estimate for this and all
     * nested templates.
     */
    private int calculateEstimatedSize(List<Map<String, String>> dataList) {
        // the size of the datalist specifies how many times we will render ourselves.
        int sizeMultiplier = dataList.isEmpty() ? 1 : dataList.size();
        int fullCalculatedSize = sizeMultiplier * estimatedSize;
        for (TemplateProcessor innerProcessor : this.innerTemplates.values()) {
            fullCalculatedSize += innerProcessor.calculateEstimatedSize(innerProcessor.defaultDataList);
        }
        return fullCalculatedSize;
    }

    private StringBuilder internalRender(int indent, StringBuilder parts, List<Map<String, String>> dataList) {
        Map<String, String> myDataMap = Map.of();
        List<TemplateSection> templateSections = templatesSectionsByIndent.get(indent);
        int templateSectionsSize = templateSections.size();
        int dataListIndex = 0;
        if (!dataList.isEmpty()) {
            myDataMap = dataList.get(dataListIndex);
        }

        // build ourself out for each map of data given
        while (true) {
            for (int i = 0; i < templateSectionsSize; i++) {
                TemplateSection templateSection = templateSections.get(i);
                switch (templateSection.templateType) {
                    case STATIC_TEXT -> parts.append(templateSection.staticData);
                    case DYNAMIC_TEXT -> parts.append(myDataMap.get(templateSection.key));
                    default -> templateSection.templateProcessor.internalRender(templateSection.indent, parts, templateSection.templateProcessor.defaultDataList);
                }

            }
            dataListIndex += 1;
            if (!dataList.isEmpty() && dataListIndex < dataList.size()) {
                myDataMap = dataList.get(dataListIndex);
                parts.append("\n").repeat(" ", indent);
            } else {
                return parts;
            }
        }
    }

    /**
     * Returns the reference to an inner template, to enable registering
     * data and sub-templates.
     */
    public TemplateProcessor getInnerTemplate(String innerTemplateKey) {
        return this.innerTemplates.get(innerTemplateKey);
    }
}


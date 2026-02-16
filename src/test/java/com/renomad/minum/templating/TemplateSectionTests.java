package com.renomad.minum.templating;

import com.renomad.minum.utils.InvariantException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;

public class TemplateSectionTests {

    @Test
    public void test_MissingKeyAndSubstring_WithStaticText() {
        var sb = new StringBuilder();
        var ex = assertThrows(TemplateRenderException.class, () -> new TemplateSection(null, null, 0, TemplateType.STATIC_TEXT));
        assertEquals(ex.getMessage(), "Invalid templateSection: TemplateSection{key='null', subString='null', indent=0, templateType=STATIC_TEXT}");

    }

    @Test
    public void test_MissingKeyAndSubstring_WithDynamicText() {
        var sb = new StringBuilder();
        var ex = assertThrows(TemplateRenderException.class, () -> new TemplateSection(null, null, 0, TemplateType.DYNAMIC_TEXT));
        assertEquals(ex.getMessage(), "Invalid templateSection: TemplateSection{key='null', subString='null', indent=0, templateType=DYNAMIC_TEXT}");
    }

    @Test
    public void test_HavingKeyAndSubstring_WithStaticText() {
        var sb = new StringBuilder();
        var ex = assertThrows(TemplateRenderException.class, () -> new TemplateSection("key", "substring", 0, TemplateType.STATIC_TEXT));
        assertEquals(ex.getMessage(), "Invalid templateSection: TemplateSection{key='key', subString='substring', indent=0, templateType=STATIC_TEXT}");
    }

    @Test
    public void test_HavingKeyAndSubstring_WithDynamicText() {
        var sb = new StringBuilder();
        var ex = assertThrows(TemplateRenderException.class, () -> new TemplateSection("key", "substring", 0, TemplateType.DYNAMIC_TEXT));
        assertEquals(ex.getMessage(), "Invalid templateSection: TemplateSection{key='key', subString='substring', indent=0, templateType=DYNAMIC_TEXT}");
    }

    /**
     * Since the incoming builder is empty, no processing takes place,
     * it just returns the empty builder.
     */
    @Test
    public void test_processSectionOutside_builderEmpty() {
        var sb = new StringBuilder();
        ArrayList<TemplateSection> templateSections = new ArrayList<>();

        StringBuilder result = TemplateProcessor.processSectionOutside(sb, templateSections, 0);

        assertTrue(sb == result);
    }

    /**
     * If there is content in the builder, then we'll add its content
     * to the list of TemplateSections
     */
    @Test
    public void test_processSectionOutside_builderNotEmpty() {
        var sb = new StringBuilder().append("hello world");
        ArrayList<TemplateSection> templateSections = new ArrayList<>();

        StringBuilder result = TemplateProcessor.processSectionOutside(sb, templateSections, 0);

        assertEquals(result.toString(), "");
        assertEquals(templateSections.getFirst().key, "hello world");
    }

    @Test
    public void test_justArrivedInside() {
        assertTrue(TemplateProcessor.justArrivedInside("hello {{ world }}", '{', 6));
        assertFalse(TemplateProcessor.justArrivedInside("{", '{', 0));
    }

    /**
     * In this test, the indent does not get used, since
     * it is only one line.
     */
    @Test
    public void test_indenting_edgeCase_NoIndent() {
        var sb = new StringBuilder();
        TemplateSection templateSection = new TemplateSection("abc", null, 5, TemplateType.DYNAMIC_TEXT);
        templateSection.render(Map.of("abc", "foo foo"), sb);
        assertEquals(sb.toString(), "foo foo");
    }

    /**
     * In this test, the indent is applied to the next line
     */
    @Test
    public void test_indenting_edgeCase_WithIndent() {
        var sb = new StringBuilder();
        TemplateSection templateSection = new TemplateSection("abc", null, 5, TemplateType.DYNAMIC_TEXT);
        templateSection.render(Map.of("abc", "foo foo\nbar bar"), sb);
        assertEquals(sb.toString(), "foo foo\n    bar bar");
    }

    @Test
    public void test_equals() {
        EqualsVerifier.forClass(TemplateSection.class).verify();
    }

}

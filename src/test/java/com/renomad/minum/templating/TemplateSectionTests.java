package com.renomad.minum.templating;

import com.renomad.minum.utils.InvariantException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;

public class TemplateSectionTests {

    @Test
    public void test_MissingKeyAndSubstring() {
        var templateSection = new TemplateSection(null, null, 0);
        assertThrows(InvariantException.class,
                "Either the key or substring must exist",
                () -> templateSection.render(null));
    }

    @Test
    public void test_HavingKeyAndSubstring() {
        var templateSection = new TemplateSection("key", "substring", 0);
        assertThrows(InvariantException.class,
                "If this object has a substring, then it must not have a key",
                () -> templateSection.render(null));
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
        TemplateSection templateSection = new TemplateSection("abc", null, 5);
        RenderingResult render = templateSection.render(Map.of("abc", "foo foo"));
        assertEquals(render.renderedSection(), "foo foo");
    }

    /**
     * In this test, the indent is applied to the next line
     */
    @Test
    public void test_indenting_edgeCase_WithIndent() {
        TemplateSection templateSection = new TemplateSection("abc", null, 5);
        RenderingResult render = templateSection.render(Map.of("abc", "foo foo\nbar bar"));
        assertEquals(render.renderedSection(), "foo foo\n    bar bar");
    }

}

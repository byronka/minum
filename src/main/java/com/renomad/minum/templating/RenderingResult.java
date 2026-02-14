package com.renomad.minum.templating;

/**
 * The result of rendering one of the {@link TemplateSection}s, used to build
 * up the full template. This is needed so we obtain information about whether our
 * user-supplied keys were applied.
 * @param renderedSection The result of rendering this section.  In the case of a
 *                        {@link TemplateSection} that takes a key, this will be
 *                        the result of replacing that with what the user provided.
 * @param appliedKey In cases where a key was replaced with a value supplied by
 *                   the user, this will supply the key that was replaced.  This is
 *                   useful to track how the supplied keys are being used in the template.
 */
public record RenderingResult(String renderedSection, String appliedKey) { }

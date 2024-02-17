package com.renomad.minum.htmlparsing;

/**
 * The different kinds of things in an HTML document.
 */
public enum ParseNodeType {
    /**
     * An HTML element.
     * <p>
     *    For example, a p (paragraph) or div (division)
     * </p>
     */
    ELEMENT,

    /**
     * String content inside an HTML element
     * <p>
     *     For example, {@code <p>Hi I am the content</p>}
     * </p>
     */
    CHARACTERS
}
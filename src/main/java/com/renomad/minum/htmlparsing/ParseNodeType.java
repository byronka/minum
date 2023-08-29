package com.renomad.minum.htmlparsing;

/**
 * The different kinds of things in an HTML document.
 */
public enum ParseNodeType {
    /**
     * An HTML element such as p or div
     */
    ELEMENT,

    /**
     * String content inside an HTML element, such
     * as {@code <p>Hi I am the content</p>}
     */
    CHARACTERS
}
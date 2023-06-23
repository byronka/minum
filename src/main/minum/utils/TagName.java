package minum.utils;


/**
 * Possible tag names per the W3C HTML spec.
 */
public enum TagName {
    // first we'll list the void elements
    AREA(true), BASE(true), BR(true), COL(true), COMMAND(true),
    EMBED(true), HR(true), IMG(true), INPUT(true), KEYGEN(true),
    LINK(true), META(true), PARAM(true), SOURCE(true),
    TRACK(true), WBR(true),

    HTML(false), HEAD(false), TITLE(false), STYLE(false),
    SCRIPT(false), NOSCRIPT(false), BODY(false), SECTION(false),
    NAV(false), ARTICLE(false), ASIDE(false), H1(false),
    H2(false), H3(false), H4(false), H5(false), H6(false),
    HGROUP(false), HEADER(false), FOOTER(false), ADDRESS(false),
    P(false), PRE(false), BLOCKQUOTE(false), OL(false), UL(false),
    LI(false), DL(false), DT(false), DD(false), FIGURE(false),
    FIG(false), CAPTION(false), DIV(false), A(false), EM(false),
    STRONG(false), SMALL(false), CITE(false), Q(false),
    DFN(false), ABBR(false), TIME(false), CODE(false), VAR(false),
    SAMP(false), KBD(false), SUB(false), SUP(false), I(false),
    B(false),

    /**
     * Used to indicate that no tag was found, to avoid use of null
     */
    NULL(false)
    ;

    public final boolean isVoidElement;

    /**
     * If this is a void element, then it is disallowed to have
     * a closing tag.  (see <a href="https://www.w3.org/TR/2011/WD-html-markup-20110113/syntax.html#void-element">void elements</a>)
     * @param isVoidElement
     */
    TagName(boolean isVoidElement) {
        this.isVoidElement = isVoidElement;
    }
}

package com.renomad.minum.htmlparsing;


/**
 * Possible tag names per the W3C HTML spec.
 * Pulled from https://www.w3.org/TR/2012/WD-html-markup-20121025/elements.html
 */
public enum TagName {
    A(false), ABBR(false), ADDRESS(false), AREA(true), ARTICLE(false),
    ASIDE(false), AUDIO(false), B(false), BASE(true), BDI(false),
    BDO(false), BLOCKQUOTE(false), BODY(false), BR(true), BUTTON(false), CANVAS(false),
    CAPTION(false), CITE(false), CODE(false), COL(true), COLGROUP(false),
    COMMAND(true), DATALIST(false), DD(false), DEL(false), DETAILS(false),
    DFN(false), DIV(false), DL(false), DT(false), EM(false), EMBED(true),
    FIELDSET(false), FIGCAPTION(false), FIGURE(false), FOOTER(false),
    FORM(false), H1(false), H2(false), H3(false), H4(false), H5(false),
    H6(false), HEAD(false), HEADER(false), HGROUP(false), HR(true),
    HTML(false), I(false), IFRAME(false), IMG(true), INPUT(true),
    INS(false), KBD(false), KEYGEN(true), LABEL(false), LEGEND(false),
    LI(false), LINK(true), MAP(false), MARK(false), MENU(false),
    META(true), METER(false), NAV(false), NOSCRIPT(false), OBJECT(false),
    OL(false), OPTGROUP(false), OPTION(false), OUTPUT(false), P(false),
    PARAM(true), PRE(false), PROGRESS(false), Q(false), RP(false),
    RT(false), RUBY(false), S(false), SAMP(false), SCRIPT(false),
    SECTION(false), SELECT(false), SMALL(false), SOURCE(true),
    SPAN(false), STRONG(false), STYLE(false), SUB(false), SUMMARY(false),
    SUP(false), TABLE(false), TBODY(false), TD(false), TEMPLATE(false), TEXTAREA(false),
    TFOOT(false), TH(false), THEAD(false), TIME(false), TITLE(false),
    TR(false), TRACK(true), U(false), UL(false), VAR(false), VIDEO(false),
    WBR(true),

    /**
     * In HTML, the doctype is the required preamble found at the top of
     * all documents. Its sole purpose is to prevent a browser from
     * switching into so-called "quirks mode" when rendering a document;
     * that is, it ensures that the browser makes a best-effort attempt at
     * following the relevant specifications, rather than using a
     * different rendering mode that is incompatible with some specifications.
     */
     DOCTYPE(true),


    /**
     * Used to indicate that no tag was found, to avoid use of null
     */
    NULL(false)
    ;

    /**
     * Void elements are disallowed to have closing tags
     */
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

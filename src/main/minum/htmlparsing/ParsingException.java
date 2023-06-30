package minum.htmlparsing;

import java.io.Serial;

/**
 * Thrown If a failure takes place during parsing in {@link HtmlParser}
 */
public class ParsingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 9158387443482452528L;

    /**
     * This constructor allows you to provide a text message
     * for insight into what exceptional situation took place.
     */
    public ParsingException(String msg) {
        super(msg);
    }
}

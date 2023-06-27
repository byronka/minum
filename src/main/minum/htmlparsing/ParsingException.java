package minum.htmlparsing;

import java.io.Serial;

public class ParsingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 9158387443482452528L;

    public ParsingException(String msg) {
        super(msg);
    }
}

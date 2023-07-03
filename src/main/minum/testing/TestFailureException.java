package minum.testing;

public class TestFailureException extends RuntimeException{
    public TestFailureException(String msg) {
        super(msg);
    }
}

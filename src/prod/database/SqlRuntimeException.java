package database;

class SqlRuntimeException extends RuntimeException {

    public SqlRuntimeException(Exception ex) {
        super(ex);
    }

    public SqlRuntimeException(String message) {
        super(message);
    }
}

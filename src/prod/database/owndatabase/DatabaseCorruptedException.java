package database.owndatabase;

public class DatabaseCorruptedException extends RuntimeException {

    public DatabaseCorruptedException(String message, Exception ex) {
        super(message);
    }
}
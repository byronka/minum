package database.stringdb;

public interface Databaseable<T> {
    DatabaseEntry toDatabaseEntry();
    T fromDatabaseEntry(DatabaseEntry m);
}

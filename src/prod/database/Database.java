package database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class Database {

    static class DbList<T> {
        final private List<T> itemsList;

        private DbList(List<T> itemsList) {
            this.itemsList = itemsList;
        }

        /**
         * Here, we let an action defined by our client
         * do something with the values in itemsList
         */
        public void actOn(Consumer<List<T>> action) {
            action.accept(itemsList);
        }

        public <R> R read(Function<List<T>, R> readAction) {
            return readAction.apply(itemsList);
        }

    }

    /**
     * The central data structure of our database
     */
    private final Map<String, DbList<?>> mainMap;
    private static Database database;

    private Database() {
        mainMap = new HashMap<>();
    }

    public static Database createDatabase() {
        if (database == null) {
            database = new Database();
        }
        return database;
    }

    /**
     * Create a new named location for some data
     */
    public <T> void createNewList(String listName, Class<T> clazz) {
        final var initialValue = new DbList<>(new ArrayList<T>());
        mainMap.put(listName, initialValue);
    }

    @SuppressWarnings("unchecked")
    public <T> DbList<T> getList(String listName) {
        return (DbList<T>) this.mainMap.get(listName);
    }
}

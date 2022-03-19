package database;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static utils.Invariants.mustBeTrue;

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
    private final Map<NameAndType<?>, DbList<?>> mainMap;
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

    record NameAndType<T>(String name, Class<T> clazz){}

    /**
     * Create a new named location for some data
     */
    public <T> void createNewList(String listName, Class<T> clazz) {
        final var initialValue = new DbList<>(new ArrayList<T>());
        final var keyValue = new NameAndType<>(listName, clazz);
        mainMap.put(keyValue, initialValue);
    }

    public <T> DbList<T> getList(String listName, Class<T> clazz) {
        final var matchingKeys = this.mainMap.keySet().stream().filter(k -> k.name.equals(listName)).sorted(Comparator.comparing(NameAndType::name)).toList();
        NameAndType<?> validKey;
        switch (matchingKeys.size()) {
            case 0: return null;
            case 1: validKey = matchingKeys.get(0);
            break;
            default: throw new RuntimeException("It should not be possible to have multiple matching keys here. keys: %s".formatted(String.join(";", matchingKeys.toString())));
        }
        final var result = this.mainMap.get(validKey);
        mustBeTrue(validKey.clazz() == clazz, "The data was stored as %s, while you requested it as %s".formatted(validKey.clazz(), clazz));
        //noinspection unchecked
        return (DbList<T>) result;
    }
}

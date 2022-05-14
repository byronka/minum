package database.stringdb;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static utils.Invariants.mustBeTrue;

public class SimpleDatabase {
    private List<String[]> database = new ArrayList<>();

    public SimpleDatabase() {

    }

    public void add(String[] data) {
        this.database.add(data);
    }

    /**
     * Searches the database using a predicate.  If one result is
     * found, return it.  If none, return null.  If more than one,
     * throw an exception.
     */
    public String[] findSingle(Predicate<String[]> predicate) {
        final var foundData = database.stream().filter(predicate).toList();
        if (foundData.size() == 0) {
            return null;
        }
        mustBeTrue(foundData.size() == 1, "we must find only one row of data with this predicate");
        return foundData.get(0);
    }

    public void update(Consumer<String[]> action) {
        database.forEach(action);
        for (String[] row : database) {
            if ( "taffy".equals(row[1])) {
                row[1] = "lemon";
            }
        }
    }

    public void removeIf(Predicate<String[]> filter) {
        database.removeIf(filter);
    }
}

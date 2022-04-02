package database.owndatabase;

import primary.dataEntities.TestThing;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static database.owndatabase.ChangeTrackingSet.toChangeTrackingSet;
import static utils.Invariants.mustNotBeNull;

public class PureMemoryDatabase {

    private DatabaseDiskPersistence diskPersistence;
    private Map<String, ChangeTrackingSet<?>> data;

    public PureMemoryDatabase(DatabaseDiskPersistence diskPersistence, Map<String, ChangeTrackingSet<?>> data) {
        this.diskPersistence = diskPersistence;
        this.data = data;
    }


    public void stop() {
        diskPersistence.stop();
    }


    public PureMemoryDatabase copy() {
        final Map<String, ChangeTrackingSet<?>> copiedData = this.data.entrySet()
                .stream()
                .collect(Collectors.toMap(x -> x.getKey(), x -> toChangeTrackingSet(x.getValue().stream().toList())));

        return new PureMemoryDatabase(diskPersistence, copiedData);
    }


    /**
     * This method is central to accessing the database.
     */
    @SuppressWarnings("unchecked")
    public <T extends IndexableSerializable<?>> DataAccess<T> dataAccess(String directoryName) {
        return (DataAccess<T>) new DataAccess<>(mustNotBeNull(data.get(directoryName)), diskPersistence, directoryName);
    }

    /**
     * returns true if all the sets of data are empty
     */
    public boolean isEmpty() {
        return data.entrySet().stream().allMatch(x -> x.getValue().isEmpty());
    }


    /**
     * Creates a default empty database with our common data sets, empty
     */
    public static PureMemoryDatabase createEmptyDatabase(DatabaseDiskPersistence diskPersistence) {
        final var dataMap = new HashMap<String, ChangeTrackingSet<?>>();
        dataMap.put(TestThing.INSTANCE.getDataName(), new ChangeTrackingSet<TestThing>());
        return new PureMemoryDatabase(diskPersistence, dataMap);
    }
}

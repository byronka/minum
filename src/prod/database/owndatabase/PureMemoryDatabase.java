package database.owndatabase;

import logging.ILogger;

import java.util.Map;
import java.util.stream.Collectors;

import static database.owndatabase.ChangeTrackingSet.toChangeTrackingSet;

public class PureMemoryDatabase {

    private final DatabaseDiskPersistence diskPersistence;
    private final Map<String, ChangeTrackingSet<?>> data;
    private final ILogger logger;

    public PureMemoryDatabase(DatabaseDiskPersistence diskPersistence, Map<String, ChangeTrackingSet<?>> data, ILogger logger) {
        this.diskPersistence = diskPersistence;
        this.data = data;
        this.logger = logger;
    }


    public void stop() {
        diskPersistence.stop();
    }


    public PureMemoryDatabase copy() {
        final Map<String, ChangeTrackingSet<?>> copiedData = this.data.entrySet()
                .stream()
                .collect(Collectors.toMap(x -> x.getKey(), x -> toChangeTrackingSet(x.getValue().stream().toList())));

        return new PureMemoryDatabase(diskPersistence, copiedData, logger);
    }


    /**
     * This method is central to accessing the database.
     */
    @SuppressWarnings("unchecked")
    public <T extends IndexableSerializable<?>> DataAccess<T> dataAccess(String directoryName) {
        final ChangeTrackingSet<?> myData = data.get(directoryName);
        if (myData == null) {
            throw new RuntimeException("There is no data schema set up for " + directoryName);
        }
        return (DataAccess<T>) new DataAccess<>(myData, diskPersistence, directoryName);
    }

    /**
     * returns true if all the sets of data are empty
     */
    public boolean isEmpty() {
        return data.entrySet().stream().allMatch(x -> x.getValue().isEmpty());
    }

}

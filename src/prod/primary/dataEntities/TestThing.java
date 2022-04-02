package primary.dataEntities;

import database.owndatabase.DatabaseDiskPersistence;
import database.owndatabase.IndexableSerializable;
import database.owndatabase.SerializationKeys;

import java.util.*;

public class TestThing extends IndexableSerializable<TestThing> {

    public static TestThing INSTANCE = new TestThing(0);

    private final int id;

    public TestThing(int id) {
        this.id = id;
    }

    @Override
    public Integer getIndex() {
        return id;
    }

    @Override
    public String getDataName() {
        return "TestThing";
    }

    @Override
    public Map<SerializationKeys, String> getDataMappings() {
        return Map.of(Keys.ID, String.valueOf(id));
    }

    @Override
    public TestThing deserialize(String serialized) {
        return DatabaseDiskPersistence.deserialize(serialized, this::convertTokensToType, Arrays.asList(Keys.values()) );
    }

    @Override
    public TestThing convertTokensToType(Map<SerializationKeys, String> myMap) {
        return new TestThing(Integer.parseInt(myMap.get(Keys.ID)));
    }

    enum Keys implements SerializationKeys {

        ID("id");

        private final String keyString;

        Keys(String keyString) {
            this.keyString = keyString;
        }

        @Override
        public String getKeyString() {
            return keyString;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestThing testThing = (TestThing) o;
        return id == testThing.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

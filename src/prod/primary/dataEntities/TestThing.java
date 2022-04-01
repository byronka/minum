package primary.dataEntities;

import database.owndatabase.IndexableSerializable;
import database.owndatabase.SerializationKeys;

public class TestThing extends IndexableSerializable {

    private final int id;

    public TestThing(int id) {
        this.id = id;
    }

    @Override
    public Integer getIndex() {
        return id;
    }

    public static String getDirectoryName() {
        return "TestThingee";
    }

    enum Keys implements SerializationKeys {

        ID("id"),
        NAME("name");

        private final String keyString;

        Keys(String keyString) {
            this.keyString = keyString;
        }

        @Override
        public String getKeyString() {
            return keyString;
        }
    }
}

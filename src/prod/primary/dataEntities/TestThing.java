package primary.dataEntities;

import database.owndatabase.DatabaseDiskPersistence;
import database.owndatabase.IndexableSerializable;
import database.owndatabase.SerializationKeys;

import java.util.*;

import static utils.Invariants.mustBeTrue;
import static utils.StringUtils.encode;

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
    public String getDirectoryName() {
        return "TestThingee";
    }

    @Override
    public Map<SerializationKeys, String> getDataMappings() {
        return Map.of(Keys.ID, String.valueOf(id));
    }

    /**
     * converts the data in this object to a form easily written to disk.
     * See [dataMappings] to see how we map a name to a value
     */
    public String serialize() {
        final var allKeys = getDataMappings().keySet().stream().map(SerializationKeys::getKeyString).toList();
        allKeys.forEach(x -> {
            mustBeTrue(!x.isBlank(), "Serialization keys must match this regex: %s.  Your key was: (BLANK)".formatted(validKeyRegex.pattern()));
            mustBeTrue(validKeyRegex.matcher(x).matches(), "Serialization keys must match this regex: %s.  Your key was: %s".formatted(validKeyRegex.pattern(), x));
        });
        return String.join(" , ", serializeDataMappings());
    }

    /**
     * Loop through the data mappings for this class,
     * serializing each one, and returning a list of
     * them as individual strings
     */
    private List<String> serializeDataMappings() {
        return getDataMappings().entrySet().stream().map(x -> "{ " + x.getKey().getKeyString() + ": " + encode(x.getValue()) + " }").toList();
    }

    @Override
    public TestThing deserialize(String serialized) {
        return DatabaseDiskPersistence.deserialize(serialized, this::convertDeserializedMapToMe, Arrays.asList(Keys.values()) );
    }

    public TestThing convertDeserializedMapToMe(Map<SerializationKeys, String> myMap) {
        return new TestThing(Integer.parseInt(myMap.get(Keys.ID)));
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

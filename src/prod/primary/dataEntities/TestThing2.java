package primary.dataEntities;

import database.owndatabase.DatabaseDiskPersistence;
import database.owndatabase.IndexableSerializable;
import database.owndatabase.SerializationKeys;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class TestThing2 extends IndexableSerializable<TestThing2> {

    public static TestThing2 INSTANCE = new TestThing2(0, "", "");

    private final int id;
    private final String favoriteColor;
    private final String favoriteIceCream;

    public TestThing2(int id, String favoriteColor, String favoriteIceCream) {
        this.id = id;
        this.favoriteColor = favoriteColor;
        this.favoriteIceCream = favoriteIceCream;
    }

    @Override
    protected Integer getIndex() {
        return id;
    }

    @Override
    public Map<SerializationKeys, String> getDataMappings() {
        return Map.of(Keys.ID, String.valueOf(id),
                Keys.FAVORITE_COLOR, favoriteColor,
                Keys.FAVORITE_ICE_CREAM, favoriteIceCream);
    }

    @Override
    public TestThing2 deserialize(String serialized) {
        return DatabaseDiskPersistence.deserialize(serialized, this::convertTokensToType, Arrays.asList(TestThing2.Keys.values()) );
    }

    @Override
    public String getDataName() {
        return "TestThing2";
    }

    @Override
    public TestThing2 convertTokensToType(Map<SerializationKeys, String> myMap) {
        return new TestThing2(
                Integer.parseInt(myMap.get(Keys.ID)),
                myMap.get(Keys.FAVORITE_COLOR),
                myMap.get(Keys.FAVORITE_ICE_CREAM)
        );
    }

    enum Keys implements SerializationKeys {

        ID("id"),
        FAVORITE_ICE_CREAM("ic"),
        FAVORITE_COLOR("c");

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
        TestThing2 that = (TestThing2) o;
        return id == that.id && Objects.equals(favoriteColor, that.favoriteColor) && Objects.equals(favoriteIceCream, that.favoriteIceCream);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, favoriteColor, favoriteIceCream);
    }
}

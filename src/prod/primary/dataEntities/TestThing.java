package primary.dataEntities;

import database.owndatabase.DatabaseDiskPersistence;
import database.owndatabase.IndexableSerializable;
import database.owndatabase.SerializationKeys;

import java.util.*;
import java.util.regex.MatchResult;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static utils.Invariants.mustBeTrue;
import static utils.Invariants.mustNotBeNull;
import static utils.StringUtils.decode;
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
        final var allKeys = getDataMappings().keySet().stream().map(x -> x.getKeyString()).toList();
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
        final var matcher = DatabaseDiskPersistence.serializedStringRegex.matcher(serialized);
        mustBeTrue(matcher.matches(), "the saved data (%s) must match the pattern (%s)".formatted(serialized, DatabaseDiskPersistence.serializedStringRegex.pattern()));
        mustBeTrue(matcher.groupCount() % 3 == 0, "Our regular expression returns three values each time.  The whole match, then the key, then the value.  Thus a multiple of 3");
        var currentIndex = 0;
        final var myMap = new HashMap<SerializationKeys, String>();
        while(true) {
            if (matcher.groupCount() - currentIndex >= 3) {
                int finalCurrentIndex = currentIndex;
                final var keys = Arrays.stream(Keys.values()).filter(x -> x.getKeyString().equals(matcher.group(finalCurrentIndex + 2))).toList();
                mustBeTrue(keys.size() == 1, "There should only be one key found");
                myMap.put(keys.get(0), decode(matcher.group(currentIndex + 3)));
                currentIndex += 3;
            } else {
                break;
            }
        }
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

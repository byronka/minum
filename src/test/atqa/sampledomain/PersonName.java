package atqa.sampledomain;

import atqa.database.SimpleDataType;
import atqa.database.SimpleSerializable;

public record PersonName(Long index, String fullname) implements SimpleDataType<PersonName> {
    public static final SimpleDataType<PersonName> EMPTY = new PersonName(0L, "");

    @Override
    public Long getIndex() {
        return index;
    }

    @Override
    public String serialize() {
        return SimpleSerializable.serializeHelper(index, fullname());
    }

    @Override
    public PersonName deserialize(String serializedText) {

        final var tokens = SimpleSerializable.deserializeHelper(serializedText);

        return new PersonName(Long.parseLong(tokens.get(0)), tokens.get(1));
    }
}

package minum.sampledomain;

import minum.Context;
import minum.database.ISimpleDataType;
import minum.database.SimpleDataTypeImpl;

public class PersonName extends SimpleDataTypeImpl<PersonName> {

    private final Long index;
    private final String fullname;

    public PersonName(Long index, String fullname) {
        this.index = index;

        this.fullname = fullname;
    }

    public static final ISimpleDataType<PersonName> EMPTY = new PersonName(0L, "");

    @Override
    public Long getIndex() {
        return index;
    }

    public String getFullname() {
        return fullname;
    }

    @Override
    public String serialize() {
        return serializeHelper(index, fullname);
    }

    @Override
    public PersonName deserialize(String serializedText) {

        final var tokens = deserializeHelper(serializedText);

        return new PersonName(Long.parseLong(tokens.get(0)), tokens.get(1));
    }
}

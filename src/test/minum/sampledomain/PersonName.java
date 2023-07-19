package minum.sampledomain;

import minum.Context;
import minum.database.AlternateSimpleDataTypeImpl;
import minum.database.ISimpleDataType;
import minum.database.SimpleDataTypeImpl;

public class PersonName extends AlternateSimpleDataTypeImpl<PersonName> {

    private long index;
    private final String fullname;

    public PersonName(Long index, String fullname) {
        this.index = index;

        this.fullname = fullname;
    }

    public static final PersonName EMPTY = new PersonName(0L, "");

    @Override
    public long getIndex() {
        return index;
    }

    @Override
    public void setIndex(long index) {
        this.index = index;
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

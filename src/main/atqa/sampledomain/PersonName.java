package atqa.sampledomain;

import atqa.database.SimpleDataType;
import atqa.utils.StringUtils;

public record PersonName(Long index, String fullname) implements SimpleDataType<PersonName> {
    public static final SimpleDataType<PersonName> EMPTY = new PersonName(0L, "");

    @Override
    public Long getIndex() {
        return index;
    }

    @Override
    public String serialize() {
        return index + " " + StringUtils.encode(fullname());
    }

    @Override
    public PersonName deserialize(String serializedText) {
        final var tokens = serializedText.split(" ");

        return new PersonName(Long.parseLong(tokens[0]), StringUtils.decode(tokens[1]));
    }
}

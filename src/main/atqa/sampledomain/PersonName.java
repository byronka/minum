package atqa.sampledomain;

import atqa.database.SimpleDataType;
import atqa.utils.StringUtils;

public record PersonName(String fullname, Long index) implements SimpleDataType<PersonName> {
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

        return new PersonName(StringUtils.decode(tokens[0]), Long.parseLong(tokens[1]));
    }
}

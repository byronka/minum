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
        final var indexEndOfIndex = serializedText.indexOf(' ');
        final var indexStartOfName = indexEndOfIndex + 1;

        final var rawStringIndex = serializedText.substring(0, indexEndOfIndex);
        final var rawStringName = serializedText.substring(indexStartOfName);

        return new PersonName(StringUtils.decode(rawStringName), Long.parseLong(rawStringIndex));
    }
}

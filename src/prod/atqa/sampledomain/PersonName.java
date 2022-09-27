package atqa.sampledomain;

import atqa.database.SimpleDataType;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record PersonName(String fullname, Long index) implements SimpleDataType<PersonName> {
    @Override
    public Long getIndex() {
        return index;
    }

    @Override
    public String serialize() {
        return index + " " + URLEncoder.encode(fullname(), StandardCharsets.UTF_8);
    }

    @Override
    public PersonName deserialize(String serializedText) {
        final var indexEndOfIndex = serializedText.indexOf(' ');
        final var indexStartOfName = indexEndOfIndex + 1;

        final var rawStringIndex = serializedText.substring(0, indexEndOfIndex);
        final var rawStringName = serializedText.substring(indexStartOfName);

        return new PersonName(URLDecoder.decode(rawStringName, StandardCharsets.UTF_8), Long.parseLong(rawStringIndex));
    }
}

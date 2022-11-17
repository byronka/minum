package atqa.auth;

import atqa.database.SimpleDataType;
import atqa.utils.StringUtils;

public record SessionId(String sessionCode, long index) implements SimpleDataType<SessionId> {

    @Override
    public Long getIndex() {
        return index;
    }

    @Override
    public String serialize() {
        return index + " " + StringUtils.encode(sessionCode());
    }

    @Override
    public SessionId deserialize(String serializedText) {
        final var indexEndOfIndex = serializedText.indexOf(' ');
        final var indexStartOfName = indexEndOfIndex + 1;

        final var rawStringIndex = serializedText.substring(0, indexEndOfIndex);
        final var rawStringName = serializedText.substring(indexStartOfName);

        return new SessionId(StringUtils.decode(rawStringName), Long.parseLong(rawStringIndex));
    }
}

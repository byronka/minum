package atqa.photo;

import atqa.database.SimpleDataType;
import atqa.database.SimpleSerializable;

import java.util.Base64;

import static atqa.utils.StringUtils.decode;

public record Photograph(Long index, byte[] photo, String photoUrl, String description) implements SimpleDataType<Photograph> {

    public static final SimpleDataType<Photograph> EMPTY = new Photograph(0L, new byte[0], "", "");

    @Override
    public Long getIndex() {
        return index;
    }

    @Override
    public String serialize() {
        return SimpleSerializable.serializeHelper(index, Base64.getEncoder().encodeToString(photo), photoUrl(), description());
    }

    @Override
    public Photograph deserialize(String serializedText) {
        final var tokens = SimpleSerializable.tokenizer(serializedText);

        return new Photograph(
                Long.parseLong(tokens.get(0)),
                Base64.getDecoder().decode(tokens.get(1)),
                decode(tokens.get(2)),
                decode(tokens.get(3)));
    }
}

package atqa.featurelogic.photo;

import atqa.database.SimpleDataType;
import atqa.database.SimpleSerializable;

import static atqa.utils.StringUtils.decode;

public record Photograph(Long index, String photoUrl, String shortDescription, String description) implements SimpleDataType<Photograph> {

    public static final SimpleDataType<Photograph> EMPTY = new Photograph(0L, "", "", "");

    @Override
    public Long getIndex() {
        return index;
    }

    @Override
    public String serialize() {
        return SimpleSerializable.serializeHelper(index, photoUrl(), shortDescription(), description());
    }

    @Override
    public Photograph deserialize(String serializedText) {
        final var tokens = SimpleSerializable.tokenizer(serializedText);

        return new Photograph(
                Long.parseLong(tokens.get(0)),
                decode(tokens.get(1)),
                decode(tokens.get(2)),
                decode(tokens.get(3)));
    }
}

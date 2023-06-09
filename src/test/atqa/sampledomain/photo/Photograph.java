package atqa.sampledomain.photo;


import atqa.database.SimpleDataType;
import atqa.database.SimpleSerializable;

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
        final var tokens = SimpleSerializable.deserializeHelper(serializedText);

        return new Photograph(
                Long.parseLong(tokens.get(0)),
                tokens.get(1),
                tokens.get(2),
                tokens.get(3));
    }
}

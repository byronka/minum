package atqa.photo;

import atqa.database.SimpleDataType;
import atqa.utils.StringUtils;

import static atqa.utils.StringUtils.decode;

public record Photograph(Long index, String photoUrl, String description) implements SimpleDataType<Photograph> {

    public static final SimpleDataType<Photograph> EMPTY = new Photograph(0L, "", "");

    @Override
    public Long getIndex() {
        return index;
    }

    @Override
    public String serialize() {
        return index + " " + StringUtils.encode(photoUrl()) + " " + StringUtils.encode(description());
    }

    @Override
    public Photograph deserialize(String serializedText) {
        final var tokens = serializedText.split(" ");

        return new Photograph(Long.parseLong(tokens[0]), decode(tokens[1]), decode(tokens[1]));
    }
}

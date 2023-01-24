package atqa.photo;

import atqa.database.SimpleDataType;
import atqa.utils.StringUtils;

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
        return index + " " + StringUtils.encode(Base64.getEncoder().encodeToString(photo)) + StringUtils.encode(photoUrl()) + " " + StringUtils.encode(description());
    }

    @Override
    public Photograph deserialize(String serializedText) {
        final var tokens = serializedText.split(" ");

        return new Photograph(
                Long.parseLong(tokens[0]),
                Base64.getDecoder().decode(tokens[1]),
                decode(tokens[2]),
                decode(tokens[3]));
    }
}

package minum.sampledomain.photo;

import minum.database.ISimpleDataType;
import minum.database.SimpleDataTypeImpl;

public class Photograph extends SimpleDataTypeImpl<Photograph> {

    private final Long index;
    private final String photoUrl;
    private final String shortDescription;
    private final String description;

    public Photograph(Long index, String photoUrl, String shortDescription, String description) {
        this.index = index;
        this.photoUrl = photoUrl;
        this.shortDescription = shortDescription;
        this.description = description;
    }

    public static final ISimpleDataType<Photograph> EMPTY = new Photograph(0L, "", "", "");

    @Override
    public Long getIndex() {
        return index;
    }

    @Override
    public String serialize() {
        return serializeHelper(index, photoUrl, shortDescription, description);
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public Photograph deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new Photograph(
                Long.parseLong(tokens.get(0)),
                tokens.get(1),
                tokens.get(2),
                tokens.get(3));
    }
}

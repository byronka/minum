package com.renomad.minum.sampledomain.photo;

import com.renomad.minum.database.DbData;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public class Photograph extends DbData<Photograph> {

    private long index;
    private final String photoUrl;
    private final String shortDescription;
    private final String description;

    public Photograph(long index, String photoUrl, String shortDescription, String description) {
        this.index = index;
        this.photoUrl = photoUrl;
        this.shortDescription = shortDescription;
        this.description = description;
    }

    public static final Photograph EMPTY = new Photograph(0L, "", "", "");

    @Override
    public long getIndex() {
        return index;
    }

    @Override
    public void setIndex(long index) {
        this.index = index;
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

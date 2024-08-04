package com.renomad.minum.sampledomain.photo;


import com.renomad.minum.database.DbData;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public class Video extends DbData<Video> {

    public static final Video EMPTY = new Video(0L, "", "", "");
    private Long index;
    private final String videoUrl;
    private final String shortDescription;
    private final String description;

    public Video(Long index, String videoUrl, String shortDescription, String description) {

        this.index = index;
        this.videoUrl = videoUrl;
        this.shortDescription = shortDescription;
        this.description = description;
    }

    @Override
    public long getIndex() {
        return index;
    }

    @Override
    protected void setIndex(long index) {
        this.index = index;
    }

    @Override
    public String serialize() {
        return serializeHelper(index, videoUrl, shortDescription, description);
    }

    @Override
    public Video deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new Video(
                Long.parseLong(tokens.get(0)),
                tokens.get(1),
                tokens.get(2),
                tokens.get(3));
    }

    @Override
    public String toString() {
        return "Video{" +
                "index=" + index +
                ", videoUrl='" + videoUrl + '\'' +
                ", shortDescription='" + shortDescription + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getDescription() {
        return description;
    }
}

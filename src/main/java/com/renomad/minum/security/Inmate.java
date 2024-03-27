package com.renomad.minum.security;


import com.renomad.minum.database.DbData;

import java.util.Objects;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * Represents an inmate in our "jail".  If someone does something we don't like, they do their time here.
 */
public final class Inmate extends DbData<Inmate> {

    /**
     * Builds an empty version of this class, except
     * that it has a current Context object
     */
    public static final Inmate EMPTY = new Inmate(0L, "", 0L);
    private Long index;
    private final String clientId;
    private final Long releaseTime;

    /**
     * Represents an inmate in our "jail".  If someone does something we don't like, they do their time here.
     * @param clientId a string representation of the client address plus a string representing the offense,
     *                 for example, "1.2.3.4_vuln_seeking" - 1.2.3.4 was seeking out vulnerabilities.
     * @param releaseTime the time, in milliseconds from the epoch, at which this inmate will be released
     *                    from the brig.
     */
    public Inmate(Long index, String clientId, Long releaseTime) {
        this.index = index;
        this.clientId = clientId;
        this.releaseTime = releaseTime;
    }

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
        return serializeHelper(index, clientId, releaseTime);
    }

    @Override
    public Inmate deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new Inmate(
                Long.parseLong(tokens.get(0)),
                tokens.get(1),
                Long.parseLong(tokens.get(2)));
    }

    public String getClientId() {
        return clientId;
    }

    public Long getReleaseTime() {
        return releaseTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Inmate inmate = (Inmate) o;
        return Objects.equals(index, inmate.index) && Objects.equals(clientId, inmate.clientId) && Objects.equals(releaseTime, inmate.releaseTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, clientId, releaseTime);
    }

    @Override
    public String toString() {
        return "Inmate{" +
                "index=" + index +
                ", clientId='" + clientId + '\'' +
                ", releaseTime=" + releaseTime +
                '}';
    }
}
package com.renomad.minum.sampledomain.auth;


import com.renomad.minum.database.DbData;
import com.renomad.minum.utils.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * A record for holding information related to a session. Typically, creation of
 * this should be handled by {@link #createNewSession(long)}
 *
 * For more about this concept, see <a href="https://en.wikipedia.org/wiki/Session_(computer_science)#Web_server_session_management">Web server sessions</a>
 */
public class SessionId extends DbData<SessionId> {

    private long index;
    private final String sessionCode;
    private final ZonedDateTime creationDateTime;

    /**
     * @param index            a simple numeric identifier that lets us distinguish one record from another
     * @param sessionCode      the sessionCode is a randomly-generated string that will be used
     *                         in a cookie value so requests can authenticate.
     * @param creationDateTime the zoned date and time at which this session was created
     */
    public SessionId(long index, String sessionCode, ZonedDateTime creationDateTime) {
        super();

        this.index = index;
        this.sessionCode = sessionCode;
        this.creationDateTime = creationDateTime;
    }

    public static final SessionId EMPTY = new SessionId(0, "", ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault()));

    /**
     * Builds a proper session, with a randomly-generated sessionCode and a creation time.  Just provide the index.
     * <br><br>
     * You might be wondering why it is necessary to provide the index.  The reason is that
     * the index is an aspect of this sessionId in the context of being one in a collection.
     * An individual SessionId only knows what its own index is, it doesn't know about its
     * siblings.  For that reason, providing a proper index is the responsibility of the
     * class which manages the whole collection.
     */
    public static SessionId createNewSession(long index) {
        return new SessionId(index, StringUtils.generateSecureRandomString(20), ZonedDateTime.now(ZoneId.of("UTC")));
    }

    @Override
    public long getIndex() {
        return index;
    }

    @Override
    public void setIndex(long index) {
        this.index = index;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public ZonedDateTime getCreationDateTime() {
        return creationDateTime;
    }

    @Override
    public String serialize() {
        return serializeHelper(index, sessionCode, creationDateTime.toString());
    }

    @Override
    public SessionId deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);

        return new SessionId(
                Long.parseLong(tokens.get(0)),
                tokens.get(1),
                ZonedDateTime.parse(Objects.requireNonNull(tokens.get(2))));
    }
}

package atqa.auth;


import atqa.database.SimpleDataType;
import atqa.database.SimpleSerializable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import static atqa.utils.StringUtils.decode;
import static atqa.utils.StringUtils.generateSecureRandomString;

/**
 * A simple record for holding information related to a session. Typically, creation of
 * this should be handled by {@link #createNewSession(long)}
 *
 * @param index            a simple numeric identifier that lets us distinguish one record from another
 * @param sessionCode      the sessionCode is a randomly-generated string that will be used
 *                         in a cookie value so requests can authenticate.
 * @param creationDateTime the zoned date and time at which this session was created
 */
public record SessionId(long index, String sessionCode, ZonedDateTime creationDateTime) implements SimpleDataType<SessionId> {

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
        return new SessionId(index, generateSecureRandomString(20), ZonedDateTime.now(ZoneId.of("UTC")));
    }

    @Override
    public Long getIndex() {
        return index;
    }

    @Override
    public String serialize() {
        return SimpleSerializable.serializeHelper(index, sessionCode, creationDateTime.toString());
    }

    @Override
    public SessionId deserialize(String serializedText) {
        final var tokens = SimpleSerializable.tokenizer(serializedText);

        return new SessionId(
                Long.parseLong(tokens.get(0)),
                decode(tokens.get(1)),
                ZonedDateTime.parse(Objects.requireNonNull(decode(tokens.get(2)))));
    }
}

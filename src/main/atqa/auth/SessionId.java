package atqa.auth;

import atqa.database.SimpleDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import static atqa.utils.StringUtils.*;

/**
 * A simple record for holding information related to a session. Typically, creation of
 * this should be handled by {@link #createNewSession(long)}
 * @param sessionCode the sessionCode is a randomly-generated string that will be used
 *                    in a cookie value so requests can authenticate.
 * @param index a simple numeric identifier that lets us distinguish one record from another
 * @param creationDateTime the zoned date and time at which this session was created
 */
public record SessionId(String sessionCode, long index, ZonedDateTime creationDateTime) implements SimpleDataType<SessionId> {

    public static final SessionId EMPTY = new SessionId("", 0, ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault()));

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
        return new SessionId(generateSecureRandomString(20), index, ZonedDateTime.now(ZoneId.of("UTC")));
    }

    @Override
    public Long getIndex() {
        return index;
    }

    @Override
    public String serialize() {
        return index + " " + encode(sessionCode()) + " " + encode(creationDateTime.toString());
    }

    @Override
    public SessionId deserialize(String serializedText) {
        final var indexEndOfIndex = serializedText.indexOf(' ');
        final var indexStartOfName = indexEndOfIndex + 1;
        final var indexEndOfName = serializedText.indexOf(' ', indexStartOfName);
        final var indexStartOfDate = indexEndOfName + 1;

        final var rawStringIndex = serializedText.substring(0, indexEndOfIndex);
        final var rawStringName = serializedText.substring(indexStartOfName, indexEndOfName);
        final var rawStringDate = serializedText.substring(indexStartOfDate);

        return new SessionId(
                decode(rawStringName),
                Long.parseLong(rawStringIndex),
                ZonedDateTime.parse(Objects.requireNonNull(decode(rawStringDate))));
    }
}

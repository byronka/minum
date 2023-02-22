package atqa.auth;

import atqa.database.SimpleDataType;
import atqa.database.SimpleSerializable;

import static atqa.utils.StringUtils.decode;

/**
 * A data structure representing authentication information for a user.
 * @param id the unique identifier for this record
 * @param username a user-chosen unique identifier for this record (system must not allow two of the same username)
 * @param hashedPassword the hash of a user's password
 * @param salt some randomly-generated letters appended to the user's password.  This is
 *             to prevent dictionary attacks if someone gets access to the database contents.
 *             See "Salting" in docs/http_protocol/password_storage_cheat_sheet.txt
 * @param currentSession If this use is currently authenticated, there will be a {@link SessionId} for them
 */
public record User(Long id, String username, String hashedPassword, String salt, String currentSession) implements SimpleDataType<User> {

    public static final User EMPTY = new User(0L, "", "", "", null);

    @Override
    public Long getIndex() {
        return id;
    }

    @Override
    public String serialize() {
        return SimpleSerializable.serializeHelper(id(), username(), hashedPassword(), salt(), currentSession());
    }

    @Override
    public User deserialize(String serializedText) {
        final var tokens = SimpleSerializable.tokenizer(serializedText);
        return new User(
                Long.parseLong(tokens.get(0)),
                decode(tokens.get(1)),
                decode(tokens.get(2)),
                decode(tokens.get(3)),
                decode(tokens.get(4))
                );
    }
}

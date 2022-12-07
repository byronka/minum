package atqa.auth;

import atqa.database.SimpleDataType;

import static atqa.utils.StringUtils.decode;
import static atqa.utils.StringUtils.encode;

/**
 * A data structure representing authentication information for a user.
 * @param id the unique identifier for this record
 * @param username a user-chosen unique identifier for this record (system must not allow two of the same username)
 * @param hashedPassword the hash of a user's password
 * @param salt some randomly-generated letters appended to the user's password.  This is
 *             to prevent dictionary attacks if someone gets access to the database contents.
 *             See "Salting" in docs/password_storage_cheat_sheet.txt
 * @param currentSession If this use is currently authenticated, there will be a {@link SessionId} for them
 */
public record User(Long id, String username, String hashedPassword, String salt, String currentSession) implements SimpleDataType<User> {

    public static final SimpleDataType<User> EMPTY = new User(0L, "", "", "", "");

    @Override
    public Long getIndex() {
        return id;
    }

    @Override
    public String serialize() {
        return id() + " " +
                encode(username()) + " " +
                encode(hashedPassword()) + " " +
                encode(salt()) + " " +
                encode(currentSession());
    }

    @Override
    public User deserialize(String serializedText) {
        final var tokens = serializedText.split(" ");
        return new User(
                Long.parseLong(tokens[0]),
                decode(tokens[1]),
                decode(tokens[2]),
                decode(tokens[3]),
                decode(tokens[4])
                );
    }
}

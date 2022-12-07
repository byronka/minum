package atqa.auth;

import atqa.database.SimpleDataType;

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
public record User(Long id, String username, String hashedPassword, String salt, String currentSession) {

    public static final SimpleDataType<User> EMPTY = new User(0, "", "", "", "");

}

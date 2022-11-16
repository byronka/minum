package atqa.auth;

import java.util.List;

public class AuthUtils {
    /**
     * Processes the request and returns a {@link Authentication} object.
     * <br>
     * More concretely, searches the cookie header in the list of headers
     * of the request and sees if that corresponds to a valid session
     * in our database.  The object returned (the Authentication object) should
     * have all necessary information for use by domain code:
     * 1. do we know this user? (Authentication)
     * 2. Are they allowed to access this resource? (Authorization)
     * etc...
     */
    public static Authentication processAuth(List<String> headers) {
        // TODO: relying merely on them having a Cookie header is so insufficient.
        // we really need to check their code against a session in our database.
        final var isAuthenticated = headers.stream().anyMatch(x -> x.startsWith("Cookie"));
        return new Authentication(isAuthenticated);
    }
}

package atqa.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static atqa.utils.Invariants.mustBeTrue;

public class AuthUtils {

    /**
     * Used to extract cookies from the Cookie header
     */
    public static final Pattern sessionIdCookieRegex = Pattern.compile("sessionid=(?<sessionIdValue>\\w+)");

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
        final var cookieHeaders = headers.stream()
                .map(String::toLowerCase)
                .filter(x -> x.startsWith("cookie"))
                .collect(Collectors.joining("; "));
        System.out.println(cookieHeaders);
        final var cookieMatcher = AuthUtils.sessionIdCookieRegex.matcher(cookieHeaders);
        final var listOfSessionIds = new ArrayList<String>();
        while (cookieMatcher.find()) {
            listOfSessionIds.add(cookieMatcher.group("sessionIdValue"));
        }
        mustBeTrue(listOfSessionIds.size() < 2, "there must be either 0 or one session id found.  Anything more is invalid");
        return new Authentication(listOfSessionIds.size() == 1);
    }
}

package atqa.testing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexUtils {

    public static String find(String regex, String data) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(data);
        return matcher.find() ? matcher.group(0) : "";
    }
}

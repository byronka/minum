package database.stringdb;

import utils.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static utils.Invariants.mustBeTrue;

public record DatabaseEntry(String classname, Map<String, String> data) {
    final static Pattern databaseEntryRegex = Pattern.compile("^class: ([^ ,]*), values: (.*)$");

    /**
     * Render a string version of the data in this class, meant to be
     * written to disk.
     */
    @Override
    public String toString() {
        // no need to URL-encode the class
        StringBuilder sb = new StringBuilder("class: ").append(classname()).append(", values: ");

        final var entries = data().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();

        var count = 0;
        for (var i : entries) {
            if (count != 0) {
                sb.append(", ");
            }
            // no need to encode the property name
            sb.append(i.getKey())
                    .append("=");
            final var value = i.getValue();
            if (value == null) {
                sb.append("%NULL%");
            } else {
                sb.append(StringUtils.encode(i.getValue()));
            }
            count++;
        }
        return sb.toString();
    }

    /**
     * Converts a string that was previously rendered using {@link #toString()} back
     * to an instance of this type.
     */
    public DatabaseEntry toDatabaseEntry(String s) {
        final var matcher = databaseEntryRegex.matcher(s);
        mustBeTrue(matcher.matches(), "we must find a match in the text " + s);
        final var classname = matcher.group(1);
        final var keyValuePairs = Arrays.stream(matcher.group(2).split(",")).toList().stream().map(String::trim).toList();
        // convert the keyValuePairs to a map for easier manipulation
        Map<String, String> myMap = new HashMap<>();
        for (var k : keyValuePairs) {
            final var split = k.split("=");
            myMap.put(split[0], StringUtils.decodeWithNullToken(split[1]));
        }
        return new DatabaseEntry(classname, myMap);
    }
}

package database.stringdb;

import java.util.Map;

public record DatabaseEntry(Class c, Map<String, String> data) {}

package minum.security;

import java.util.List;
import java.util.Map;

public interface ITheBrig {
    // Regarding the BusyWait - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @SuppressWarnings({"BusyWait"})
    ITheBrig initialize();

    void stop();

    void sendToJail(String clientIdentifier, long sentenceDuration);

    boolean isInJail(String clientIdentifier);

    List<Map.Entry<String, Long>> getInmates();
}

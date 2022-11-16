package atqa.logging;

import atqa.utils.ThrowingSupplier;

import java.util.ArrayList;
import java.util.List;

/**
 * This implementation of {@link ILogger} is specially
 * designed to assist with tests where something goes
 * awry in a separate thread.  This way, when something
 * gets logged about an error condition there, we can
 * easily search for it later in our test.
 * <pr>
 * Anything that is logged with this class will get
 * stored into a publicly-available field, "loggedMessages",
 * which you can inspect afterwards.
 */
public class TestRecordingLogger implements ILogger {

    public final List<String> loggedMessages;

    /**
     * See {@link TestRecordingLogger}
     */
    public TestRecordingLogger() {
        loggedMessages = new ArrayList<>();
    }

    public void logDebug(ThrowingSupplier<String, Exception> msg) {
        try {
            loggedMessages.add(msg.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void logTrace(ThrowingSupplier<String, Exception> msg) {
        try {
            loggedMessages.add(msg.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void logAsyncError(ThrowingSupplier<String, Exception> msg) {
        try {
            loggedMessages.add(msg.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void logImperative(String msg) {
    }

}

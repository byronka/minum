package com.renomad.minum.database;

import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.*;

public class InspectableLockTests {

    /**
     * We have a static method in the {@link InspectableLock}
     * just to handle the situation when {@link InspectableLock#getLockOwner()}
     * returns null, since it provides a "best-effort" to give us the thread
     * and it may be null.
     */
    @Test
    public void testHandlesNullProperly() {
        String result = InspectableLock.getLockOwnerIdString(null);

        assertEquals("null", result);
    }


    @Test
    public void testHappyPathProperly() {
        var myThread = Thread.currentThread();
        String result = InspectableLock.getLockOwnerIdString(myThread);

        assertFalse(result.equals("null"));
    }
}

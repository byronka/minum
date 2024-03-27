package com.renomad.minum.utils;

import org.junit.Test;


import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.renomad.minum.testing.TestFramework.*;

public class ExtendedExecutorTests {

    @Test
    public void test_isDoneWithoutException() {
        Foo1 foo = new Foo1();
        assertTrue(ExtendedExecutor.isDoneWithoutException(foo, null));
        assertFalse(ExtendedExecutor.isDoneWithoutException(foo, new Exception()));
        assertFalse(ExtendedExecutor.isDoneWithoutException(() -> System.out.println("testing"), new Exception()));
        assertFalse(ExtendedExecutor.isDoneWithoutException(() -> System.out.println("testing"), null));
        assertFalse(ExtendedExecutor.isDoneWithoutException(null, null));
        foo.isDoneState = false;
        assertFalse(ExtendedExecutor.isDoneWithoutException(foo, null));
    }

    @Test
    public void test_afterExecuteCode() {
        Foo1 foo1 = new Foo1();

        var myCancelExc = new CancellationException("testing throw of CancellationException");
        foo1.getAction = () -> {
            throw myCancelExc;
        };

        Throwable result1 = ExtendedExecutor.afterExecuteCode(foo1, null);
        assertTrue(result1 == myCancelExc);


        Foo2 foo2 = new Foo2();
        Throwable result2 = ExtendedExecutor.afterExecuteCode(foo2, null);
        assertEquals(result2.getMessage(), "Testing ExecutionException");

        Foo3 foo3 = new Foo3();
        ExtendedExecutor.afterExecuteCode(foo3, null);
        assertTrue(Thread.currentThread().isInterrupted());

        Exception testingAnException = new Exception("Testing an exception");
        Throwable result4 = ExtendedExecutor.afterExecuteCode(foo1, testingAnException);
        assertTrue(result4 == testingAnException);

        foo1.getAction = () -> {
            System.out.println("all is well");
            return null;
        };

        Throwable result5 = ExtendedExecutor.afterExecuteCode(foo1, null);
        assertTrue(result5 == null);
    }

    static class Foo1 implements Future<Integer>, Runnable {

        boolean isDoneState = true;
        Supplier<Integer> getAction = null;
        @Override public void run() {}
        @Override public boolean cancel(boolean mayInterruptIfRunning) {return false;}
        @Override public boolean isCancelled() {return false;}
        @Override public boolean isDone() {return isDoneState;}
        @Override public Integer get() {return getAction.get();}
        @Override public Integer get(long timeout, TimeUnit unit) {return null;}
    }

    static class Foo2 implements Future<Integer>, Runnable {

        boolean isDoneState = true;
        @Override public void run() {}
        @Override public boolean cancel(boolean mayInterruptIfRunning) {return false;}
        @Override public boolean isCancelled() {return false;}
        @Override public boolean isDone() {return isDoneState;}
        @Override public Integer get() throws ExecutionException {throw new ExecutionException(new Exception("Testing ExecutionException"));}
        @Override public Integer get(long timeout, TimeUnit unit) {return null;}
    }

    static class Foo3 implements Future<Integer>, Runnable {

        boolean isDoneState = true;
        @Override public void run() {}
        @Override public boolean cancel(boolean mayInterruptIfRunning) {return false;}
        @Override public boolean isCancelled() {return false;}
        @Override public boolean isDone() {return isDoneState;}
        @Override public Integer get() throws InterruptedException {throw new InterruptedException("testing an InterruptedException");}
        @Override public Integer get(long timeout, TimeUnit unit) {return null;}
    }
}

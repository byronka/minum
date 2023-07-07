Project Loom
============

"Project Loom" is the name given to the work being done to enable green threads in the Java VM.
This will allow us to have millions of threads, which is perfect for our use case.

Here is some code to sample this.  While running this code, have Java Mission Control running
and keep an eye on the live threads.  In the first chunk of code (which uses ordinary threads),
each thread will take up an OS thread and about 2 megabytes of memory - about 8 gigabytes gets used.

In the next chunk, it's using virtual threads, and you will see it use maybe 30 threads and maybe
50 megabytes.  Quite a difference!

```java
     System.out.println("Starting lots of threads"); {

     var threadFactory = Thread.ofPlatform().factory();
     try (var executor = Executors.newThreadPerTaskExecutor(threadFactory)) {
         IntStream.range(0, 10_000).forEach(i -> {
             executor.submit(() -> {
                 Thread.sleep(Duration.ofSeconds(100));
                 return i;
             });
         });
     }
    }

     System.out.println("Starting virtual threads");{

     try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
         IntStream.range(0, 10_000).forEach(i -> {
             executor.submit(() -> {
                 Thread.sleep(Duration.ofSeconds(100));
                 return i;
             });
         });
     }
    }
```

Still, the issue remains: When I try using this virtual thread executor, my tests fail
in seemingly capricious ways.  Not good.  Still waiting on this to stabilize.

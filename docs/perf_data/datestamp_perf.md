Performance experiments
=======================

Experiment 1 - date and time stamp during HTTP response
---------------------------------------------------------------------

It was my impression that getting the current date and time might slow down the HTTP response
a bit, so I ran an experiment.  The following code took 686 milliseconds to run. I am therefore
not concerned with its performance.

```java

for(var i = 0; i < 1_000_000; i++) {
    ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.RFC_1123_DATE_TIME);
}

```
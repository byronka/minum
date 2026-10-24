Sample domain
=============

This package, "com.renomad.minum.sampledomain", contains a small sample program over many
of the Minum features, used for two purposes:

1) for some of our tests to have a full-fledged application to target
2) for messing around with a sample application using Minum, e.g. for performance analysis.

Access this application from the command line by running `make run_sampledomain` and
then visiting http://localhost:8080

At that point, the capabilities are:

1. Auth - for registering a user and logging in, necessary for most other capabilities
2. Sample domain - a simplistic program to add names to a list
3. Photos - a program to upload and view photos
4. Throw an exception - upon being clicked, this will cause an exception in the application, useful
   for viewing a typical stacktrace from the application and behavior seen on the client.

This is also useful to check basic performance.  I do this by using apache benchmark:

    $ ab -k -c 20 -n 1000 http://localhost:8080/
    This is ApacheBench, Version 2.3 <$Revision: 1843412 $>
    Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
    Licensed to The Apache Software Foundation, http://www.apache.org/
    
    Benchmarking localhost (be patient)
    Completed 100 requests
    Completed 200 requests
    Completed 300 requests
    Completed 400 requests
    Completed 500 requests
    Completed 600 requests
    Completed 700 requests
    Completed 800 requests
    Completed 900 requests
    Completed 1000 requests
    Finished 1000 requests
    
    
    Server Software:        minum
    Server Hostname:        localhost
    Server Port:            8080
    
    Document Path:          /
    Document Length:        45 bytes
    
    Concurrency Level:      20
    Time taken for tests:   0.125 seconds
    Complete requests:      1000
    Failed requests:        0
    Non-2xx responses:      1000
    Keep-Alive requests:    1000
    Total transferred:      228000 bytes
    HTML transferred:       45000 bytes
    Requests per second:    7977.15 [#/sec] (mean)
    Time per request:       2.507 [ms] (mean)
    Time per request:       0.125 [ms] (mean, across all concurrent requests)
    Transfer rate:          1776.16 [Kbytes/sec] received
    
    Connection Times (ms)
    min  mean[+/-sd] median   max
    Connect:        0    0   0.4      0       5
    Processing:     0    2   1.7      2      21
    Waiting:        0    2   1.6      2      20
    Total:          0    2   1.7      2      21
    
    Percentage of the requests served within a certain time (ms)
    50%      2
    66%      3
    75%      3
    80%      3
    90%      4
    95%      5
    98%      7
    99%      8
    100%     21 (longest request)
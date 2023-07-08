Response time test
==================

#### In short: 19,500 responses per second

```shell
$ ab -k -c20 -n 1000000 "http://localhost:8080/hello?name=byron"
This is ApacheBench, Version 2.3 <$Revision: 1843412 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking localhost (be patient)
Completed 100000 requests
Completed 200000 requests
Completed 300000 requests
Completed 400000 requests
Completed 500000 requests
Completed 600000 requests
Completed 700000 requests
Completed 800000 requests
Completed 900000 requests
Completed 1000000 requests
Finished 1000000 requests


Server Software:        minum
Server Hostname:        localhost
Server Port:            8080

Document Path:          /hello?name=byron
Document Length:        11 bytes

Concurrency Level:      20
Time taken for tests:   51.167 seconds
Complete requests:      1000000
Failed requests:        0
Keep-Alive requests:    1000000
Total transferred:      150000000 bytes
HTML transferred:       11000000 bytes
Requests per second:    19543.92 [#/sec] (mean)
Time per request:       1.023 [ms] (mean)
Time per request:       0.051 [ms] (mean, across all concurrent requests)
Transfer rate:          2862.88 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.0      0       4
Processing:     0    1   0.4      1      33
Waiting:        0    1   0.4      1      29
Total:          0    1   0.4      1      33

Percentage of the requests served within a certain time (ms)
  50%      1
  66%      1
  75%      1
  80%      1
  90%      1
  95%      2
  98%      2
  99%      2
 100%     33 (longest request)

```
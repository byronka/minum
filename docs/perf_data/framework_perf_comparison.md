Framework performance comparison
=================================


|                                                                                                                                            | Minum  | Spring |
|--------------------------------------------------------------------------------------------------------------------------------------------|--------|--------|
| Compile time<br>\> mvn clean compile<br>\> (minum) make clean jar                                                                          | 3.4    | 1.5    |
| Start Time (Sec)<br>\> java -jar app.jar                                                                                                   | 0.3    | 1.6    |
| Requests Per Second<br>\> ab -k -c 20 -n 1000000 http://localhost/8080/hello/John <br>Single thread                                        | 19k    | 18k    |
| Requests Per Second with -Xmx16m <br>\> ab -k -c 20 -n 1000000 http://localhost:8080/hello/John <br>Single thread                          | 19k    | 10k    |
| Requests Per Second with -Xmx64m <br>\> wrk -t12 -c400 -d30s --latency http://localhost:8080/hello/John <br>12 threads and 400 connections | 8.9k   | 4.2k   |
| Memory Consumption - Heap Usage (Mb)                                                                                                       | 50     | 95     |
| Memory Consumption - Heap usage with -Xmx16m (Mb)                                                                                          | 8.6    | 10.5   |
| Jar Size With Dependencies (Mb)                                                                                                            | 0.2    | 19     |
Size statistics: 
===============

_(all lines of code are production - non-test-code)_

used [cloc](https://github.com/AlDanial/cloc/) for lines-of-code calculations. 

Javalin is 53 times the size of Minum when you take its dependencies into account.

Why did I pick on Javalin?

>"A simple web framework for Java and Kotlin"
>"Lightweight - Javalin is just a few thousand lines of code on top of Jetty, and its 
> performance is equivalent to raw Jetty code. Due to its size, it's very easy to 
> reason about the source code.


Lines of code table
-------------------

| Minum  | Javalin |
|--------|---------|
| 4,799  | 255,384 |

Basis for Javalin's size:
-------------------------

| Component               | LOC     |
|-------------------------|---------|
|                         |         |
| Javalin                 | 5,485   |
|                         |         |
| essential dependencies  |         |
| slf4j-api               | 3,070   |
| slf4j-simple            | 423     |
| jetty-server            | 37,176  |
| jetty-webapp            | 7,126   |
| websocket-jetty-server  | 890     |
| websocket-jetty-api     | 800     |
| ***subtotal***          | 49,485  |
|                         |         |
| optional dependencies:  |         |
| jvmbrotli               | 895     |
| brotli4j                | 1,492   |
| jackson-databind        | 73,242  |
| jackson-module-kotlin   | 1,407   |
| gson                    | 9,042   |
| ***subtotal***          | 86,078  |
|                         |         |
| test dependencies:      |         |
| junit-jupiter-api       | 5,810   |
| assertj-core            | 48,153  |
| mockito-core            | 21,080  |
| unirest-java            | 6,720   |
| Java-Websocket          | 6,899   |
| okhttp                  | 19,814  |
| moshi                   | 5,311   |
| moshi-kotlin            | 264     |
| jetty-unixdomain-server | 285     |
| ***subtotal***          | 114,336 |
|                         |         |
| ***total***             | 255,384 |

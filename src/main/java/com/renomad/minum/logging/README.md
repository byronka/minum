Logging
=======

These classes define minimalistic programs to enable decent logging.  The performance is
satisfactory, and there is nothing too crazy going on. 

Essentially, each call to a logger method will receive a closure following the
`RunnableWithDescription` interface.  It will pop that into the `LoggingActionQueue`, which
will keep them in order as they are eventually output to standard out.
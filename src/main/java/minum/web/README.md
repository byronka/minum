Web Code
========

This directory has the code necessary for our system to communicate using the Hyper-Text
Transfer Protocol (HTTP).

There's quite a bit here, since there's a bunch of moving parts to making this work.

Please note that as of this writing, the http2 code is just begun and non-functional.

_FullSystem_ - This ties together much of the functionality here for startup.  If you
examine the code, particularly `FullSystem.start()`, you will see how many variables
get initialized.


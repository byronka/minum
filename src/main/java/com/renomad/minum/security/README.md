Security
========

These are special classes whose purpose is to keep an eye on misbehavior by clients who
may be trying to attack.  For example, perhaps a client is trying to force the server to
use an old, insecure version of TLS.  That is suspicious, so we'll put them in a kind of
time-out for 10 seconds, to slow them down.
Security
========

These classes keep an eye on misbehavior by attackers.  For example, perhaps a client 
is trying to force the server to use an old, insecure version of TLS.  That is suspicious, 
so we'll put them in time-out for 10 seconds, to slow them down.
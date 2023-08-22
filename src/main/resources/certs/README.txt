This keystore was found at https://github.com/openjdk/jdk/tree/master/test/jdk/javax/net/ssl/etc

I have _no idea_ how to build this myself.  I tried a variety of approaches, but when trying
to follow their notes, they casually allude to

    "This can be generated using hacked (update the keytool source code so that
    it can be used for version 1 X.509 certificate) keytool command:"

I mean ... hello? talk about unhelpful.

I tried to run the commands listed in their readme, ignoring the note about a hacked tool,
but I did not succeed.  By simply copying their generated file, however, all was well.
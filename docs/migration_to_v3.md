To migrate to minum v3.0:
=========================

IMPORTANT NOTE: these changes should only be done on source code in a code-versioning system, like Git.
As changes are written to disk, take great care to examine what has changed.  This is a way to avoid
adding subtle bugs.

For the HTTP status code adjustments
------------------------------------

run the following on the command line, and make sure you see "nothing to commit, working tree clean"

    git status
    
now we can make changes safely - if we don't like the result, we reset back to where we were with `git reset --hard`
Run this command to list all the spots in the code that should get affected by the replacement command.  Every 
line should have an HTTP status code constant, something like `_303_SEE_OTHER`.
    
    find src/ -type f -name "*.java" -exec grep --color -HE "_[0-9][0-9][0-9]_" {} \;
    
check the lines carefully.  Each match should be highlighted in color, with the associated filename on the left.
the previous command changed nothing on disk.  Confirm by running `git status` and getting `nothing to commit...`
    
now, the dangerous part - the actual search and replace. This command will search for the following pattern: an underscore, three digits, an underscore.  It will append "CODE" to the beginning of each match, throughout the src directory, wherever it finds a file with a ".java" suffix:
    
    find src/ -type f -name "*.java" -exec sed -i 's/_[0-9][0-9][0-9]_/CODE&/g' {} \;
    
Once run, it will make many changes on disk.  Review the changes using git, or your source-control system of choice.
    
    git status

You can see a word diff using Git, like this:

    git diff -U0 --word-diff=color


For the constant name adjustments:
----------------------------------

the Minum system constants are in the com.renomad.Constants file, and their pattern has been adjusted. They are
no longer uppercase characters separated by underscore - they are now camel-case.

Here is the command to replace these values throughout your codebase.  Adjust the directory as needed:

    find src/ -type f -name "*.java" -exec sed -i \
    -e "s/SECURE_SERVER_PORT/secureServerPort/g" \
    -e "s/SERVER_PORT/serverPort/g" \
    -e "s/HOST_NAME/hostName/g" \
    -e "s/DB_DIRECTORY/dbDirectory/g" \
    -e "s/STATIC_FILES_DIRECTORY/staticFilesDirectory/g" \
    -e "s/LOG_LEVELS/logLevels/g" \
    -e "s/USE_VIRTUAL/useVirtual/g" \
    -e "s/KEYSTORE_PATH/keystorePath/g" \
    -e "s/KEYSTORE_PASSWORD/keystorePassword/g" \
    -e "s/REDIRECT_TO_SECURE/redirectToSecure/g" \
    -e "s/MAX_READ_SIZE_BYTES/maxReadSizeBytes/g" \
    -e "s/MAX_READ_LINE_SIZE_BYTES/maxReadLineSizeBytes/g" \
    -e "s/MAX_QUERY_STRING_KEYS_COUNT/maxQueryStringKeysCount/g" \
    -e "s/MOST_COOKIES_WELL_LOOK_THROUGH/mostCookiesWellLookThrough/g" \
    -e "s/MAX_HEADERS_COUNT/maxHeadersCount/g" \
    -e "s/MAX_TOKENIZER_PARTITIONS/maxTokenizerPartitions/g" \
    -e "s/SOCKET_TIMEOUT_MILLIS/socketTimeoutMillis/g" \
    -e "s/KEEP_ALIVE_TIMEOUT_SECONDS/keepAliveTimeoutSeconds/g" \
    -e "s/VULN_SEEKING_JAIL_DURATION/vulnSeekingJailDuration/g" \
    -e "s/IS_THE_BRIG_ENABLED/isTheBrigEnabled/g" \
    -e "s/SUSPICIOUS_ERRORS/suspiciousErrors/g" \
    -e "s/SUSPICIOUS_PATHS/suspiciousPaths/g" \
    -e "s/START_TIME/startTime/g" \
    -e "s/EXTRA_MIME_MAPPINGS/extraMimeMappings/g" \
    -e "s/STATIC_FILE_CACHE_TIME/staticFileCacheTime/g" \
    -e "s/USE_CACHE_FOR_STATIC_FILES/useCacheForStaticFiles/g" \
    -e "s/MAX_ELEMENTS_LRU_CACHE_STATIC_FILES/maxElementsLruCacheStaticFiles/g" \
    {} \;


Removal of update
-----------------

The behavior of Db.update and Db.write has been consolidated into Db.write.

To make this adjustment, replace all uses of Db.update with Db.write.  Take care
to adjust any nearby code or comments that expected the update command. One
way to do this is by compiling the code:

```shell
./mvnw compile
```

You will see complaints like this:

```shell
Compilation failure:
Foo.java: cannot find symbol       
  symbol:   method update
```

These should have specific classes and line numbers indicating which are the methods to adjust

Other breaking changes
----------------------

Here are all the other breaking changes - examine these in your code:

* FunctionalTesting now requires the hostname and port
* `FunctionalTesting.send` now requires a byte array body parameter
* Methods in `FileUtils` now don't throw `IOException`
* `TheBrig.sendToJail` now returns true if succeeded
* `TheBrig.getInmates` now returns a list of `Inmate` instead of `List<Map.Entry<String, Long>>`

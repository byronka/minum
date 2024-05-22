v3.2.0 - May 20, 2024
---------------------

* New ability to inject code before and after regular endpoint processing.  See registerPreHandler
  and registerLastMinuteHandler in WebFramework.
* Extra testing across the board.
* Improve documentation.
* Remove dead code in ExtendedExecutor.
* Improve performance test for templating.  Uses parallel processing to show more realistic speed.
* Include indent when rendering template.  Often, a user will render a value with newlines in
  a template.  This adjustment tracks the indent of the key in the template and then
  applies that to subsequent lines of the key values.

As an example, with a template like `foo bar {{ color }}`:

  before:
  
  ```
  foo bar red
  blue
  orange
  ```
  
  after:
  
  ```
  foo bar red
          blue
          orange
  ```


v3.1.1 - Apr 5, 2024
--------------------

* bug fix: multipart data partitioning.  The multipart-form decoding was not properly 
  trimming the last two bytes - carriage return and line feed.  That is to say, data
  being received by multipart-form had two bytes appended at the end.

v3.1.0 - Mar 29, 2024
---------------------

* Adjust tests to work on Mac
* Include "throws Exception" on ThrowingRunnable.  This obviates handling checked
  exceptions when adding endpoints - the thrown exception will get caught and
  logged at `throwingRunnableWrapper`.

v3.0.0 - Mar 27, 2024
--------------------

* Deeper testing - nearly 100% test coverage
* Breaking changes (see [migration guide](docs/migration_to_v3.md)): 
  * Spelling of constant values
  * Name of the HTTP codes
  * Removal of the `update` method in `Db`
  * FunctionalTesting now requires the hostname and port
  * `FunctionalTesting.send` now requires a byte array body parameter
  * Methods in `FileUtils` now don't throw `IOException`
  * `TheBrig.sendToJail` now returns true if succeeded
  * `TheBrig.getInmates` now returns a list of `Inmate` instead of `List<Map.Entry<String, Long>>`
  * `FullSystem.close` becomes `FullSystem.shutdown`
* Refactorings / documentation improved all over
* New helper methods in TestLogger for searching logs
* In Logger, if the `ActionQueue` is stopped or null, will fall back to use `System.out.printf`
* In TestLogger, new method `doesMessageExist` which handles the common usage better, that is:
  
  `assertTrue(logger.findFirstMessageThatContains("foo foo did a foo").length() > 0);`
  
  becomes
  
  `assertTrue(logger.doesMessageExist("foo foo did a foo"));`

* Truncate timestamp in logging to microseconds
* Modify the `ThrowingRunnable` and other functional interfaces to handle exceptions better
* Raised the bar for testing throughout the system.  Methods
  were adjusted to expose code for testing.  Linting tools recommended naming modifications.
* Documentation was written to help users through migration - see docs/migration_to_v3.md.

* The `update` and `write` methods in the database shared so much functionality, it made sense to 
  combine them.  There are now only two methods in Db handling data modification.  If creating new data, 
  set the index to 0 on the object extending DbData. 

  This code creates a new item in the database:
  
      db.write(new Foo(0, 2, "a"));
  
  This code updates an item:
  
      db.write(new Foo(1, 2, "a"));
  
  Adding a new item with a positive index will fail - the database has to generate the index
  for you.  The returned value from `write` will provide the new index.

* ThrowingRunnable and other functional interfaces now properly handle exceptions.  It is
  no longer necessary to try-catch a checked exception when using one - anything thrown will
  be logged.  See ThrowingRunnable.
* `FullSystem.close` renamed to `FullSystem.shutdown`
* `Headers.make` now just requires one parameter, `Context`
* `RequestLine.EMPTY` renamed to `RequestLine.empty`
* Bug fixes

v2.5.3 - Mar 9, 2024
--------------------

* Improve testing, refactoring
  * Deeper testing on InputStreamUtils
  * Rudimentary testing for FullSystem
  * Improved configuration for mutation tests
  * Adjust port in web tests to avoid conflicts with other tests

v2.5.2 - Mar 7, 2024
--------------------

* Documentation improvements

v2.5.1 - Feb 11, 2024
---------------------

* Edge case improvements. Refactorings.

1) If the client sends us a message body that has a different length than 
   the content-length header, we will handle it by just logging to debug level.
2) If we have determined a client is trying to attack us again while already
   in the brig, we will update their duration.
3) If a ForbiddenUseException bubbles up to the top, we'll add that client to
   the brig.
4) Refactoring Body - does not use a Context object, so remove it.

v2.5.0 - Feb 10, 2024
---------------------

* Add GZIP compression.  This does not require any modifications by
  users - but now the Minum web server will reply with GZIP compression
  if the client browser sends a request that includes an accept-encoding
  header with a value that includes "gzip".  This will only apply to 
  text data.

v2.4.0 - Jan 31, 2024
---------------------

* Adjust documentation for ActionQueue.enqueue. The documentation was
  incorrectly suggesting it was necessary to return null.
* Add failure message option for all assertions.  This way it is possible
  to include better help when a test fails, across all assertion types.

v2.3.3 - Dec 27, 2023
---------------------

* Added a pre-filter to avoid nulls in SearchUtils.findExactlyOne, which
  helps avoid some null pointer exceptions.
* Remove ParsingException from being thrown in the BodyProcessor.  If any
  issues take place with parsing the body, it will be logged as a debug
  issue and the body will continue with processing, just without having 
  determined any key-value pairs.

v2.3.2 - Dec 15, 2023
---------------------

* cannot rely on synchronized code with virtual threads. The hope was that using the 
  synchronizedMap method would provide thread-safe access to this data.
  That has not proven out, and the guide to virtual threads in Java specifically warns against
  use of the synchronized keyword.

v2.3.1 - Dec 9, 2023
--------------------

* It was found that when a client requested a directory, the system would
  throw an IOException and log an async_error.  This fixes that, so that
  it is merely "file not found".

v2.3.0 - Nov 19, 2023
---------------------

* New ability to inject custom properties into Constants, which will provide
  some better flexibility when testing.  For example, you may want to run
  concurrent tests with different server ports, or different database folders.
* TestFramework.buildTestingContext now allows injecting a properties file
  to use when creating the Constants object.
* Make Constants.getConfiguredProperties public to enable tests to more easily
  use all the default properties, with programmatically-defined customizations

v2.2.1 - Oct 24, 2023
---------------------

* Make code for converting comma-delimited strings to array more robust.
  Now, extra spaces are much less likely to confuse it.

v2.2.0 - Oct 14, 2023
---------------------

* Provide ability to get all the keys returned in the key-value pairs
  of a Response body: body.getKeys().  Useful for some situations where you are dealing
  with dynamic fields.
* Documentation improvements.
* Increased default for maximum tokens allowed to 1000. This basically
  corresponds to how many fields you can have on a page.  It is customizable
  in the configuration at MAX_TOKENIZER_PARTITIONS
* Update version numbers for some Maven plugins, allowing nicer reports, etc.
* Add Template HTML element

v2.1.2 - Oct 14, 2023
---------------------

* Fix to bug in templating code.  It was not handling an unmatched 
  double-closing-bracket properly.  Also adjusting so if there is a
  double-opening-bracket without a closing set, it will throw a new
  custom exception.

v2.1.1 - Oct 9, 2023
--------------------

* Fix to bug in output encoding for HTML attributes, in StringUtils.encodeAttr().
  There were a couple extra characters that needed encoding.

v2.1.0 - Sep 30, 2023
---------------------

* Milder complaint if user lacks minum.config file.  Before, if the user did
  not have a minum.config in the directory where they started Minum, it would
  halt with an error message showing text for a configuration.  Now, the code
  will continue on with a warning about the missing configuration and instead
  use reasonable defaults.
* fixing a bug in the configuration settings for extra mime types ("EXTRA_MIME_MAPPINGS"),
  where a lack of value for that property would cause the system to fail on startup.
* Updated the default value for MAX_READ_LINE_SIZE_BYTES from 500 to 1024, along with
  an adjustment to the error message shown in the logs when the max was encountered.
  To provide context: this property exists to set a reasonable limit to what a
  maximum header size could be, to prevent certain security attacks, or to properly
  handle broken user agents.  It was noticed that on localhost, testing against 
  multiple servers could cause so many cookies to exist that it would exceed the
  500 byte limit.  Now, it is clearer when this limit has been encountered, and
  the value is adjustable in the configuration - see MAX_READ_LINE_SIZE_BYTES in
  minum.config.
* Extra documentation in methods, and added a tutorial "getting started"
* Added a new constant to control the number of elements in the file LRU cache,
  called MAX_ELEMENTS_LRU_CACHE_STATIC_FILES

v2.0.0 - Sep 26, 2023
---------------------

* Renamings to better align with HTTP specification
* Better correctness of database code, prevent some race conditions through locking

v1.1.1 - Sep 22, 2023
---------------------

* Linter suggestions

v1.1.0 - Sep 21, 2023
---------------------

* Improved documentation
* Improved templating - less strict with whitespace around keys
* Adjust default time for use in TheBrig
* Use webapp for static and template files

v1.0.0 - September 19, 2023 - Git commit hash: 5bcf5802652a
-----------------------------------------------------------

* Remove StaticFileCache - see 25763cfe
* Incorporate Maven
* Improve testing - using JUnit rather than custom test framework
* Various bug fixes, refactoring, and documentation
* Adjust to allow testing virtual threads on Windows - needed sleeps after closing sockets
* Rename app.config to minum.config
* Make most classes "final" to make it clear they are not expected to be extended
* Better form/multipart handling - now provides headers per partition 
* Adjust files to match typical Java/Maven patterns

v0.1.0 - August 21, 2023 - Git commit hash: 385d47e566
------------------------------------------------------

* Beta release


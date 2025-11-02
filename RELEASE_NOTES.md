Release notes
=============

* This is a list of the major versions, which are those requiring the developer to adjust
  their code.  From August 2023 to August 2024, there were 8 major versions. Version 8 has
  remained stable.
  * 8: Streaming videos, _August 2024_
  * 7: Read bodies more efficiently, _August 2024_
  * 6: Streaming responses, _July 2024_
  * 5: Refactoring, _July 2024_
  * 4: Refactoring, _June 2024_
  * 3: Code coverage at 100%, _March 2024_
  * 2: Refactoring, _September 2023_
  * 1: Refactoring, Maven as buildtool, _September 2023_
  * 0: Beta release, _August 2023_

v8.3.0 Nov 1, 2025
------------------

* Improve database performance
  * Minum's original database stores each item in its own file. This was sufficient for smaller 
  data sets, but not for large. This new design improves on that.
  * Each database change will now append to a file, being one of the fastest ways to write 
  to disk. In this way, a million rows can be added in seconds.  Following that, in the 
  consolidation phase, changes are squashed down to unique values, enabling fast system 
  startup.  A database file with a million unique entries can be loaded in about a second.
* New constants added to `minum.config` to control maximum append-file size and consolidation-file size.
  They are MAX_DATABASE_APPEND_COUNT and MAX_DATABASE_CONSOLIDATED_FILE_LINES
* Refactoring tests
* Better documentation
* New safety feature - if the user tries to register an index too late - that is, after the data
  has been loaded to memory - an exception will now be thrown.
* Fixing bugs: 
  * If the user tried finding an item by index as the first action after
    initializing a database, previously it would fail to load the data.  It now properly loads
    the data if needed.
  * In the database, if a user registered an index after data already loaded, the system would 
    not complain.  Now, it will throw an exception if that happens.
  * In the tutorial, the line of code for running functional testing was wrong


v8.2.0 Aug 6, 2025
------------------

Make it faster

* New database feature: _indexed data_

  "Indexed data" means 1000x faster data access in some cases. By indexing the data,
  it is associated with keys - in other words, it is a map of strings to lists of data.
  In cases where the keys are unique identifiers for each element of data, the list will
  contain just one single item, thus enabling O(1) requests for data.

* Templating performance improvements
* New templating feature: nested templates
* HTTP processing performance improvements
* Better documentation

v8.1.2 Jul 31, 2025
-------------------

* Fix a bug in the ActionQueue - handle throwables
   It was found that when an Error (versus an Exception) was encountered, it would kill
   the thread of the ActionQueue.  Errors are things like out-of-memory errors.  This might
   happen if the function to run in a loop iteration required too much memory.  It's fine to
   crash out of that particular run, but not fine to crash the thread entirely.
* The system will complain if duplicate endpoints are registered 
   If there is a developer error of registering the same endpoint more than once, (that is,
   a verb plus endpoint, such as GET /foo), the system will thrown an exception right away, so 
   there is less chance of incorporating subtle bugs.
* General documentation improvements all around: In the JavaDocs and on the root README page.
  * Documentation improvements to IRequest
  * Clearer documentation about change to InputStreamUtils.read(), with test
  * noticed a component of StatusLine that isn't used except for testing -
      moved it to a testing section
* Prevent registering paths with a prefixed slash. This is a typical pattern from other
  frameworks.  Minum differs from that common pattern, on the basis that it provides
  no clear benefit.  A typical (pseudocode) path registration will look like the
  following, note the path has no special symbol prefix: `register(GET, "foo", doFoo)`. Naturally,
  deeper paths may be defined, like `register(GET, "foo/bar", doFoo)`

v8.1.1 - Apr 26, 2025
---------------------

* Better documentation for HtmlParseNode

v8.1.0 - Apr 12, 2025
---------------------

* Add toString to HtmlParseNode
  This enables a capability to process HTML code, adjust it, and render it out as HTML
* Account for more compressible types in responses
  Responses of a textual type are easily compressed.  Before, we would only look for a mime
  type in the Content-Type head which started with "text".  Now we use a regular expression
  to look for more types.
* Improved documentation

v8.0.7 - Feb 7, 2025
--------------------

Minor adjustments:

1. No need to include sitemap.xml as a suspicious path in the minum.config, it is a path
   most search engines will try on a site, it is not an attack.
2. If the developer makes a mistake and returns a null instead of a valid IResponse interface
   from a web handler endpoint, we fail faster, and with a clearer message, whereas before
   the null response would rattle around through some methods before causing an exception with
   an unclear error message and in an unexpected part of the code.

v8.0.6 - Jan 15, 2025
---------------------

handle bad URL-encodings better - simply skip

It was discovered that if a user sent a request with improperly-formed URL-encoded data, it would
sometimes cause a 500 error.  This was not a significant issue, but ergonomically the system should
not react so strongly for a relatively minor issue.

This adjustment takes a milder approach, simply moving on to the next key-value pair and logging
about the action.


v8.0.5 - Nov 24, 2024
---------------------

Fixing security vulnerabilities

* improved regex for finding bad file paths
* Initial updates to be much stricter about file location and allowed chars
* adjust for greater flexibility with sending large files, with safety
* Better handling of redirect
  * when redirecting, check that the target location is a valid URI, and include a body (suggested by the spec)
* adjust test to work on Mac
* create version of test to use HTTP/1.0, which doesn't cause keep-alive by default.


v8.0.4 - Nov 11, 2024
---------------------

Big improvements with small adjustments:

- Code is modularized.  Thanks SentryMan!  Some code needed to adjust for this, specifically
  that searching for the cert location in WebEngine.java
- TagInfo.java was adjusted to enable better searching by users.  Some new HTML node search 
  methods are in the test directory, in SearchHelpers.java, for your reference.
- Documentation improved
- Logger now allows construction of descendant classes which share a queue, enabling smooth
  continuous logging without fragmented interleaving of text.
- Updated versions of testing dependencies

v8.0.3 - Sep 15, 2024
---------------------

* Bug fix for reading request bodies

A bug was introduced in version 7.0.0 that is resolved in version 8.0.3

_Description_: It was noticed that request bodies sent to the Minum server were being 
rejected.  Investigating deeper, it was discovered that the variable used to count the 
number of partitions in a request body was a class variable, and was never getting reset 
to 0.  This bug had no workaround.

_Fix_: The variable is initialized to zero at the proper time.  A test was created to confirm correct behavior.

v8.0.2 - Sep 7, 2024
--------------------

* Handling edge conditions in Body better
* Updating Pitest dependency
* Documentation - improving JavaDocs

Body methods like .asString(key) or .getPartitionHeaders() require particular body formats. If the
body is not in those formats, throw an exception, which will help developers recognize they are
using the wrong methods.

For examples, see BodyTests.java

v8.0.1 - August 27, 2024
------------------------

Extra documentation, put back constant for configuring max readline size

* Documentation was added in key areas to improve user experience of framework API
* It was found that when the server was sent requests with large headers, such as
  JWTs, the length exceeded the default of 1024.  Putting this back as a configurable
  property in minum.config.
* Making InputStreamUtils package-private - it is not intended to be user-facing

v8.0.0 - Aug 17, 2024
---------------------

Implement partial range requests, enabling video scrubbing

When a user requests a video and scrubs through, the browser will send requests for small portions
of the file, using the "range" header.  Minum now properly responds to a common subset of this header,
specifically a single-range.  Multiple ranges are ignored.

Breaking changes:
* `Response.buildLargeFileResponse` takes different parameters and may return different status codes
  depending on the needs.
* Methods in `FileUtils` did not need to take a logger for parameter since the methods have access
  to the logger field in the instance.

Other adjustments:
* `toString` methods were adjusted to avoid printing array contents - these could be very large and
  would overwhelm the log statements.
* More options made available for sending data in SocketWrapper - using an offset and length from
  an array, and sending individual bytes.
* The `StatusCode` enum now includes all status codes.

Bugs fixed:
* large files had extra bytes appended in version 7.0.0 when using `Response.buildLargeFileResponse`.  This is now corrected.
* 

v7.0.0 - Aug 3, 2024
--------------------

Handle incoming body better

Breaking changes: 
* Request and Response have had interfaces extracted, and the WebFramework.registerPath method
  has been adjusted to take a parameter expecting those interfaces.  This was done because close
  testing of the handler requires an instantiated Request object, and building this as a concrete
  implementation was more arduous than the alternative of using an interface.
* Request has converted from a record to a class, with appropriate getter methods renamed:
  * headers() becomes getHeaders()
  * body() becomes getBody()
  * requestLine() becomes getRequestLine()
  * remoteRequester() becomes getRemoteRequester()
* constants removed from configuration file:
  * MAX_READ_LINE_SIZE_BYTES - permanently set to 1kb.  a kilobyte is a sane default for the maximum header line we expect to read.
  * MAX_QUERY_STRING_KEYS_COUNT - permanently set to 50 key-value pairs, which is certainly enough for a query in a URL.
  * MOST_COOKIES_WELL_LOOK_THROUGH - this shouldn't have even been configurable - it's for a sample authentication program.
  * MAX_HEADERS_COUNT - permanently set to 70, which is the most headers our server should handle, and that should be way more than enough.
  * MAX_BODY_KEYS_URL_ENCODED - removed entirely.  Since the developer is in charge of what they are sending, we'll just let them send whatever.
                                the difference between this and the query string is that the body is only read
* Handling multipart/form-data is done differently
  * Previously, the intention was making reasonable assumptions to avoid overly 
    complicating the API.  However, due to the way multipart data is sent by browsers,
    it was necessary to change.  Now, the request type is indicated by its property Body.getBodyType.
    Users must access their data in different ways, depending on the bodytype.  For url-encoded forms,
    continue to use .asBytes and .asString, but for multipart it is necessary to use .getPartitionHeaders
    and getPartitionByName to get partitions, and then to examine the contentDisposition.
* Both multipart and url-encoded data are pulled from the network by custom InputStream implementations.  This provides
  flexibility and consistency - developers may choose to pull the whole body like before, which is convenient,
  but if they want to pull larger data, it is supported through getUrlEncodedDataIterable and
  getMultiPartIterable in BodyProcessor.

* New getter added to Request:
  * getSocketWrapper - The `Request` object was previously of type "record", which provides getters for its data automatically.
    It was necessary to convert `Request` to a class, so we could control the `getBody()` method better.  Now, when calling
    to `getBody()`, it will process the data off the socket at that time.  Previously, the server would process every 
    incoming body, taking memory to do so.  This gives an opportunity to choose whether to read the whole body at once, or
    to pull in the data in more fine-grained fashion by using `getSocketWRapper()`.  Thanks to this adjustment, the 
    security concerns around reading large bodies is somewhat mitigated, and so `MAX_READ_SIZE_BYTES` is no longer needed.
* Updated versions of some test-tool and maven plugin dependencies.


v6.0.0 - July 7, 2024
---------------------

New feature: streaming output from server

Breaking change: Response constructors converted to factory methods.  Order of parameters adjusted in
certain cases for greater consistency.

A missing capability of the Minum server was to send large files.  Yes, it was
reasonable to send files up to around several megabytes, but before this change, 
the data would take room in the server's memory.  If the server were making a large
file available and there were many requests, it could easily eat up the entire
memory.

Now, the Response object and WebFramework have had their code adjusted to enable this
without undue requirements.  The constructors in `Response` were converted to factory methods, and some of these are
intended to be used for streaming large data, such as `buildLargeFileResponse` and `buildStreamingResponse`.

There is no limit to the file size being served.  Memory usage will be minimally impactful.

#### To migrate to version 6 more easily:

Do a global search and replace: "new Response" -> "Response.buildLeanResponse".
That will fix a bunch of the cases.  The rest are likely "Response.buildResponse", but update those as necessary.  In several
cases, it will be required to reorder the parameters - the extraHeaders parameter is now the second parameter
in almost all the Response factory methods.  In some cases, you will find calls to a now-deleted constructor
which would provide a status code and body but no extra headers.  These should be fixed to include the necessary headers,
such as `Map.of("Content-Type", "text/plain")`


v5.0.0 - July 1, 2024
---------------------

Excellent test coverage enables fearless refactoring.

It is closing in on a year past beta. We have stability and high test 
coverage, let's apply the recommendations from linting tools, informed 
by our tests and usage scenarios.

Improvements:
* Several class methods and constructors would pass mutable data, leading to the possibility of
  subtle bugs.  Where possible, these have been corrected.
* Made several methods package-private, to lower scope.  Removed methods if possible - e.g. see `Context`.
* Consistency and conventionality improvements.
* Applying recommendations from linting tools that would forestall bugs, such as setting the charset
  instead of relying on the system default.
* Organizing the files better.  All files are now in subpackages of "minum".
* Improved documentation.
* Removed need for `Context` object during construction of some classes.
* Added new tools for regular examination of the code - information is provided during compilation
  and running `make lint`.

Moved to different package:
* Context (globally adjust like this: `find src/ -type f -name "*.java" -exec sed -i 's/com\.renomad\.minum\.Context/com.renomad.minum.state.Context/g' {} \;`)
* Constants (globally adjust like this: `find src/ -type f -name "*.java" -exec sed -i 's/com\.renomad\.minum\.Constants/com.renomad.minum.state.Constants/g' {} \;`)
* ActionQueue (globally adjust like this: `find src/ -type f -name "*.java" -exec sed -i 's/com\.renomad\.minum\.utils\.ActionQueue/com.renomad.minum.queue.ActionQueue/g' {} \;`)

Removed:
* Context.getFileUtils - instead of obtaining from Context, build: `new FileUtils(logger, constants);`
* Several other public methods from Context that only related to Minum internals

Adjusted:
* PathDetails is no longer a record.  Its methods moved to be prefixed with "get" with adjusted capitalization
* TagInfo, same thing
* HtmlParseNode, same thing
* On TagInfo, the attributes are further encapsulated - it is no longer possible to grab the collection.  Get an attribute with "getAttribute()"

Removed use of `Context` in constructors:
* Headers
* RequestLine

Moved:
* RequestLine.PathDetails was moved to its own file.  It can now be called on its own, "PathDetails" (globally adjust like 
  this: `find src/ -type f -name "*.java" -exec sed -i 's/RequestLine\.PathDetails/PathDetails/g' {} \;`)

v4.0.3 - June 22, 2024
----------------------

* Templates will now complain with a TemplateRenderException if the user supplies
  keys that end up not being used.  For example, in this template: `Hello {name}`,
  then if the user provides keys of `Map.of("name", "world", "foo", "bar")`, rendering
  will return an exception with a message, `No corresponding key in template found for these keys: foo`
  Adding this functionality slightly slowed down the template processor (35k/sec to 32k/sec), but
  it is still faster than the web handler (20k/sec), which is the bottleneck in this situation.
  Correctness and simplicity is more important than speed in this situation.
* SPECIAL NOTE: Because the templates are stricter, you may find your application complaining
  where it was lenient before.  Take care to notice the issues, if any exist.
* Make the key in LRUCache a generic (the value was already generic)

v4.0.2 - June 14, 2024
----------------------

* Minor fix, follow-up to previous update - request handling timer code moved to record
  timing after the request begins.
* Improve HtmlParseNodes.innerText to return valuable data when receiving more than one node.

v4.0.1 - June 9, 2024
---------------------

* Minor fix - request handling timer was outside the keep-alive loop, showing
  incorrect timing in the log statements.

v4.0.0 - June 8, 2024
---------------------

* Noticed that the utils package had a dependency on the web package
  in the FileUtils class.  Moved code to avoid this cyclical dependency.
* Disentangle methods in the web code to make stacktraces and debugging simpler
* Configuration
  * Removed flag to redirect to HTTPS from the HTTP endpoint. This behavior
    is now provided by writing appropriate code in WebFramework.preHandler.  Examples
    are provided in the documentation for the method.
    * Related: removed REDIRECT_TO_SECURE property
  * Removed ability to choose non-virtual threads.  Having two major modes of thread
    execution increases the testing surface area unnecessarily.  Since Java 21 is
    required, will just use the primary intention.
    * Related: removed USE_VIRTUAL property
    * Related: Removed ExtendedExecutor class and makeExecutorService method
  * Increased default maximum for query string count from 20 to 50
  * Changed default value for using virtual threads to true
  * Changed default value for using the brig to true
  * Including the default values as text in the minum.config file
  * Rename MAX_TOKENIZER_PARTITIONS to MAX_BODY_KEYS_URL_ENCODED
  * Better documentation in the configuration file
* Database
  * Made Db.stop() a publicly available method
  * Db.values now returns an unmodifiable collection.  This was always the intent,
    but by using Collections.unmodifiableCollection, this concept is more
    strictly enforced.
* TheBrig
  * Calling `.stop()` on TheBrig will now stop its associated Db.
  * More informative log messages in TheBrig.
  * Better locking 

v3.2.1 - May 26, 2024
---------------------

* Adjust maximum partitions in serializer.  A bug was found: text that was large but
  but still reasonably sized (it was more than ten thousand lines) was 
  causing a failure in templating. The serializer utility allowed a maximum of ten thousand 
  "partitions" (i.e. lines) before throwing an exception.  While it does make sense to 
  set a maximum on any loop, ten thousand was too low.  This value was raised to ten 
  million, based on the concept that sending an HTML template with a million lines would 
  be absurdly high, and then putting a safety factor multiple on top of that.
* Extra testing

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


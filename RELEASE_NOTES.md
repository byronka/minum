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


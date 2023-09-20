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


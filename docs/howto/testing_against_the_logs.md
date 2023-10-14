HOWTO - Testing against the logs
================================

You will occasionally want to write tests that inspect the logs, maybe to verify that
an expected action took place.  In the case of asynchronous code, this is a fairly
common need.  It is often difficult to have precise control over when something will
occur.  

For example, if you use the ActionQueue to run an action, it will run in the future.  The
ActionQueue may have other processing to do, but will eventually get your work done.
In that case, it may be necessary to inspect the previous 10 log entries (or some other number)
to look for one that confirms the expected action happened.

Here is an example:

```java
... attack the server ...
assertFalse(logger.findFirstMessageThatContains("looking for a vulnerability").isBlank());
```
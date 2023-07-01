Remove Static Variables
=======================

Okie doke, I really put myself in a hole on this one.  

So here's the thing.  static variables seem like a nice thing. When a variable is
associated with a particular object, the scope is lowered, but what about when that 
variable's value would apply to *more* than just one instance?  

Let me give you a real-life example from our little framework here.

The ActionQueue is used as a kind of conveyor-belt-machine-thingy.  You pop your
work in the feeder, and eventually it gets it done.  

Or maybe I'll slightly improve that metaphor like this: it receives the
new work into the back of a queue, while at the same time a loop is running and
pulling items off the front of the queue and doing whatever that item says.

Cool.

Everything works ok, until the time comes to shut it all down.  There needs to be 
a place _above_ all the instantiated ActionQueues from whose high perch I may look
down on them all and systematically destroy them all.  To date, this was done by
a static variable in the ActionQueue class.  But I'm realizing that may not have
been the best approach.

---

The specific issues I am encountering are: 
1. When the tests run, sometimes the program cleanly exits and sometimes it doesn't.
2. When the program runs, sometimes it's easy to gracefully kill and sometimes it's not.
3. Trying to debug and see what's going wrong with static values is difficult.
4. I have seen repeated bugs throughout the history of this project that have been difficult
   to reproduce and only lately have realized it's because of all these statics, which
   are essentially global variables.

If you ever wanted an example of a non-toy project where statics / global values caused
pain, here ya go.

---

When something is a static variable, and it's mutable, it's a global value. And
anything could change it at any time.  And that could make it really hard to
understand when things fail.  But not only that! Java's classloader treats
the static values a bit differently, seemingly, though I haven't quite gotten
to the level of certainty about how much that impacts us.  But at this time, I
have an entire class of static values, Constants.java, which is showing black
magic behavior.  For example, I was debugging while I had a breakpoint on some
code that used one of those static values from Constants, and _omg the value
was null it can't be null that's impossible_ but what's more, the null pointer
exception got eaten by something and never showed up.

I have static values that rely on static code that runs at instantiation of a class. 
I have done wrong.  The only penitence I can perform is to overhaul the application,
closing down the scope.  Static values have global scope, that must be drawn down
to a much lower scope to avoid this pain.

Approach:

1. global values are the worst.  Systematically adjust so they are held within an
   instance.
2. static methods are ok as long as they don't rely on any state anywhere.  However,
   many of my methods would benefit from having access to the logger.  For that 
   reason, the plan is to get pretty much everything into an instance.  I want
   a crystal-clear (or maybe ... marginally less muddy) scoping of my system's parts.


Approach for using statics from here on:
----------------------------------------

* true, literal constants.  if any processing is required to build the constant, it's off the 
  table, except for:
  * [null objects](https://en.wikipedia.org/wiki/Null_object_pattern), because the alternative is
    purely worse[^1].
* helper utility methods that require no state - functional-style.
* complex methods should not be static, because I may need to put logging in them, which is state.
* Use a context object to hold items that have broader scope, such as 
  logging, regular expressions, running threads I'll need to kill, ExecutorService, etc.
* static factory methods are allowed, but they should receive ILogger so we can log.

[^1]: It would require us to do context.emptyObjects().EmptyFoo() instead of Foo.EMPTY, a plainly
      worse outcome with minimal benefits.

List of statics to adjust
-------------------------

(yuck.  Sigh...)

* ActionQueue.allQueues
* ActionQueue.killAllQueues
* Constants.java values (all of them)
* AuthUtils.sessionIdCookieRegex
* BodyProcessor.parseUrlEncodedForm
* BodyProcessor.splitKeyAndValue
* BodyProcessor.multiformNameRegex
* CryptoUtils.bytesToHex
* CryptoUtils.createPasswordHash
* FileUtils.writeString
* FileUtils.deleteDirectoryRecursivelyIfExists
* FileUtils.readTemplate
* FileUtils.readFile
* FileUtils.makeDirectory
* FullSystem.createSystemRunningMarker
* FullSystem.getConfiguredProperties
* FullSystem.properties
* Headers.extractHeaderInformation
* Headers.getAllHeaders
* Headers.contentLengthRegex
* HtmlParser.parse
* HtmlParser.processState
* HtmlParser.enteringTag
* HtmlParser.exitingTag
* HtmlParser.addingToken
* HtmlParser.processTag
* HtmlParser.processTag
* HtmlParser.State.buildNewState
* InputStreamUtils.readUntilEOF
* InputStreamUtils.readChunkedEncoding
* InputStreamUtils.readLine
* InputStreamUtils.read
* Logger.convertLoggingStringsToEnums
* Logger.showWhiteSpace
* Logger.printf
* LoopingSessionReviewing.determineSessionsToKill
* LoopingSessionReviewing.isLive
* StartLine.extractStartLine
* StartLine.extractVerb
* StartLine.extractPathDetails
* StartLine.extractMapFromQueryString
* StartLine.getHttpVersion
* ... most of StartLine ...
* StatusLine.extractStatusLine
* StatusLine.statusLinePattern
* StatusLine.statusLineRegex
* pull tokenizer out of StringUtils into an instance.  It's too complex to be a static helper function.
* TemplateProcessor.buildProcessor
* Tests should be encapsulated into an object before running.
* TheRegister.registerDomains
* TheRegister.setupSampleDomains
* TheRegister.setupListPhotos
* TheRegister.setupUploadPhotos
* TheRegister.buildAuthDomain
* UnderInvestigation.isClientLookingForVulnerabilities - because these methods rely on state
* UnderInvestigation.isLookingForSuspiciousPaths - because these methods rely on state
* WebFramework, basically the whole thing









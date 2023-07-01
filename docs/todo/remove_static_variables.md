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
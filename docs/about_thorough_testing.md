from https://news.ycombinator.com/item?id=18685748

100% branch, line coverage means nothing. It's about logical coverage. What are you testing
for? You are not testing lines of code, but logic.

Dwayne Richard Hipp on Dec 15, 2018:

Right. The actual standard is called "modified condition/decison coverage" or MC/DC. In languages
like C, MC/DC and branch coverage, though not exactly the same, are very close.

Achieving 100% MC/DC does not prove that you always get the right answer. All it means is that your tests are so
extensive that you managed to get every machine-code branch to go in both directions at least once. It is a high
standard and is difficult to achieve. It does not mean that the software is perfect.

But it does help. A lot. When I was young, I used to think I could right flawless code. Then I wrote SQLite, and it got
picked up and used by lots of applications. It will amaze you how many problems will crop up when your code runs on in
millions of application on billions of devices.

I was getting a steady stream of bug reports against SQLite. Then I took 10 months (2008-09-25 through 2009-07-25) to
write the 100% MC/DC tests for SQLite. And after that, the number of bug reports slowed to a trickle. There still are
bugs. But the number of bugs is greatly reduced. (Note that 100% MC/DC was first obtained on 2009-07-25, but the work
did not end there. I spend most of my development time adding and enhancing test cases to keep up with changes in the
deliverable SQLite code.)

100% MC/DC is just an arbitrary threshold - a high threshold and one that is easy to measure and difficult to cheat -
but it is just a threshold at which we say "enough". You could just as easily choose a different threshold, such as 100%
line coverage. The higher the threshold, the fewer bugs will slip through. But there will always be bugs.

My experience is that the weird tests you end up having to write just to cause some obscure branch to go one way or
another end up finding problems in totally unrelated parts of the system. One of the chief benefits of 100% MC/DC is not
so much that every branch is tested, but rather that you have to write so many tests, and such strange, weird,
convoluted, and stressful tests, that you randomly stumble across (and fix) lots of problems you would have never
thought about otherwise.

Another big advantage of 100% MC/DC is that once they are in place, you can change anything, anywhere in the code, and
if the tests all still pass, you have high confidence that you didn't break anything. This enables us to evolve the
SQLite code much faster than we could otherwise, using relatively few eyeballs.

Yet another advantage of 100% MC/DC is that you are really testing compiled machine code, not source code. So you worry
less about compiler bugs. "Undefined behavior" is a big bugbear with C. We worry less than others about UB because we
have tested the output of the compiler and we know that the compiler did what we wanted, even if the official C-language
spec didn't require it to. We still avoid UB, and SQLite does not currently contain any UB as far as we know. But is is
nice to know that even if we missed some UB in the code someplace, it probably doesn't matter.
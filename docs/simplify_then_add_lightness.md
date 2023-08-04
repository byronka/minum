Simplify, then add Lightness
============================

_February 27, 2017 by Richard Wellum_

Before I got into software engineering, my studies were in other
engineering disciplines. One of my idols in engineering is Colin
Chapman, of Lotus; he would operate on a fraction of a budget of those
around him, and at them all through creativity and thinking outside of
the box. Even now, 50 years on, his influence and innovations are
still visible in Formula 1 car design. The quote “simplify, then add
lightness” was one of Chapman’s responses when asked how his cars were
so successful – it’s a quote I really like, because it applies very
well to other fields of engineering, in particular software.

At the time when Lotus entered Formula 1, the sport was dominated by
big heavy powerful cars. The Lotus design philosophy was very
different; they took the approach of trying to build the simplest and
most nimble cars possible, recognizing that the biggest gains in
performance would come from removing weight (even if that meant less
power) and improving cornering and acceleration. Another of his quotes
on this is “Adding power makes you faster on the straights.
Subtracting weight makes you faster everywhere.” In software there are
two main ways we can apply these principles; the first is in how we
organize ourselves, and the second is in the design of the things we
build.

_Simplify…_

Simple designs for a racing car offer a huge advantage over
complicated rivals, because they are easier to maintain. A complicated
design has more things that can go wrong, and when they do go wrong it
is more difficult to design and fix. This should be painfully familiar
to anyone working in software engineering, and yet as a whole we keep
producing over-complicated solutions. Perhaps part of the problem is
that our industry has a lot of very clever people, and building
complicated things can be kind of fun, and a way to show off people’s
ability to build these things. Perhaps it stems from a lack of
understanding, or poor focus on the underlying need. I often see
relatively simple business problems that have a disproportionately
complicated software solution, and the results are never pretty. The
ability to extend a complicated solution is much lower, and the cost
of adding these extensions rises to the point where it becomes no
longer viable.

Things like the Clean Code movement go some way to addressing these
problems at a low level; the main principle behind Clean Code is to
write code for human readability with a strong focus on clarity in
each individual module. This is a great start, but it’s still common
to see well-applied attention to detail at this level, but the big
picture is a complicated overall system design. Approaches like
Microservices are one way that systems design can be designed to have
smaller and simple components, but where it’s very easy to make big
mistakes in the overall system design and create a mess by
over-engineering and introducing boundaries that aren’t appropriate. I
see many teams who have built a complicated monolith, and believe the
solution is to build Microservices. They apply the same design
thinking, and end up building a set of complicated “Microservices”
that are coupled together, and actually worse than the monolith
because all the challenges of distributed computing have been added
with none of the benefits realized. The thing that is lacking in this
case is often the ability to strip away complexity, and identify the
simplest possible solution that could work. As it happens, the
simplest possible solution that could work often turns out not just to
work, but to be a very good solution.

Equally, quite often one of the most effective ways to build a highly
performant solution is to look for simple and elegant designs. The
worst performing systems I see are often full of entire components
that are not needed, and rather than optimizing them, the solution is
to remove them entirely, re-routing the application into a simpler
format, which is less prone to bottlenecks.

_… Then add lightness_

The big benefit that a lightweight car had over its rivals was its
rate of acceleration, and ability to change direction. The same is
true of a lightweight team structure; the ability to build and change
momentum quickly allows a small team to outperform a larger team,
where the overheads and weight that come with that size mean that a
larger team will often deteriorate in performance as process and
bureaucracy – ironically, the things that Agile methodology advocates
removing, but that creep back over time unless kept in check – take
their toll. The big front-engine cars that the Lotus design philosophy
made obsolete are the equivalent of the software team fallacy that is
adding more and more people to a project in an attempt to increase
delivery speed by adding “power” – this normally has the opposite
effect. The concept of a “two-pizza team” is fairly well known; that
is to have a team size that can be fed by two pizzas, i.e. 5-6 people.
A team with less people that that can still be successful, but as a
team grows above that in size there is an argument for breaking the
team down into smaller units. For me, a nicely balanced 2-pizza
software team could be 3 developers, 1 domain expert/business analyst
who works as part of the team, and 1-2 QA/test specialists.

Another way we can improve the health of software delivery by “adding
lightness” is in the judicious and ruthlessly prioritized choice of
features. There is a trend emerging that simple well-designed tools
that do one job but do it very well are winning over bigger more
complicated tools that try to do much more – look at things like Slack
vs email, mobile apps vs software suites, Trello vs Microsoft Project,
and countless other examples. There is a lot of value in delivering a
focused set of features that complement each other, and that value
sharply decreases as features get away from that original targeted
selling point. Software projects that have a multi-year roadmap of
features with no clear priority are much more likely to fail by simply
delivering the wrong thing, where they might succeed by delivering
focused value and responding to customer feedback. It’s also very
important to try and add lightness to the end user’s experience. The
features that resonate the most with customers are often the ones that
simplify some cumbersome workflow, or remove friction from a task they
are trying to perform.

from http://richardwellum.com/2017/02/simplify-then-add-lightness/

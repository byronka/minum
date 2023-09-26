HTML parsing
=============

This is a minimalistic html parser, essentially meant to work on your
own html5 markup text, for testing purposes.  For example, you may wish
to run fast tests that get HTTP results and want to inspect those in a
more robust way than regex.

See, for example, com.renomad.minum.web.FunctionalTesting.searchOne(), which is
used in the tests at com.renomad.minum.FunctionalTests.

The primary parsing code is found in HtmlParser.java.  Apologies in advance - 
this is hand-written parsing code.  It's gross.
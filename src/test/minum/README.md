Testing
=======

The main() function is in the Tests class, in the root level. That is to say, when you 
run the tests, that is where the ball starts rolling.

Note that main() calls to some other functions to run tests, particularly unitAndIntegrationTests().  If 
you add new tests, just follow the patterns in that function.

Also, new tests should use the patterns demonstrated in test files like minum.sampledomain.SampleDomainTests, such as:
- declaring a class variable for the TestLogger, and declaring a constructor that takes a TestLogger and assigns it
  to the class variable
- declaring a tests() method that takes an ExecutorService
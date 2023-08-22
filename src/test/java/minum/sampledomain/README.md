Sample domain
=============

It is important to understand that the following hints are critical to correctly-functioning code.

See the sample files here as a template for developing your own business needs.  PersonName is just
sample data - notice how it supports database persistence by implementing DbData.  Check
the function implementations for `getIndex`, `serialize`, and `deserialize` as good examples for your own data.

Also note key aspects of the SampleDomain class, which is the code that uses PersonName.  It 
uses a Db class for each type of data it uses (in this case, that is PersonName).

The functions in SampleDomain follow a signature of:

     public Response FUNCTION_NAME_HERE (Request r)

Just as it looks, these functions receive the HTTP "request" information and return a HTTP "response". See 
those classes for more information.

Note that SampleDomain is also demonstrating usage of the AuthUtils class.  This is able to determine
authentication by examining the headers in the HTTP request.

See how the class and related method are "registered" in the class titled "minum.TheRegister".  Use 
the same patterns in your own code to register each new business need.

This should keep your systems plain and maintainable.
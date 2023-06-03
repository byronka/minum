Sample domain
=============

It is important to understand that the following hints are critical to correctly-functioning code.

See the sample files here as a template for developing your own business needs.  PersonName is meant as
sample data - notice how it supports database persistence by implementing SimpleDataType.  Check
the function implementations for getIndex, serialize, and deserialize as good examples for your own data.

Also note key aspects of the SampleDomain class, which is the code that uses PersonName.  It 
instantiates the DatabaseDiskPersistenceSimpler (DDPS) class for the data it uses (in this case, that is PersonName).

Note also that an AtomicLong labeled "newPersonIndex" is instantiated (that is, it is attached to a particular instance
of the class, and thus may vary between instances), and is used to assign new indexes to new data. It may seem unusual
to have potentially different instances of this index, but understand that when the application starts, it will
instantiate just one instances of these classes.  Allowing each instance to have its own index lets us test
multiple databases of similar data concurrently (of course, these databases will need to store their data in different directories,
or else naturally we would have interference).

Whenever you create a new data, you will assign it the new index like this:

    new PersonName(newPersonIndex.getAndAdd(1), nameEntry);

Whenever data is changed (created, modified, or deleted), it is important to follow it up with a call
to the DDPS class to carry out that same action on the disk.  The database in this system is primarily 
memory-based, with a followup to write to disk.  The only time we read from disk is when the system is 
initially starting up.

Observe how in the constructor for SampleDomain, we have code for reading data from disk:

    personNames = diskData.readAndDeserialize(PersonName.EMPTY);

Practice this same pattern in your own code.

The functions in SampleDomain follow a signature of:

     public Response FUNCTION_NAME_HERE (Request r)

Just as it looks, these functions receive the HTTP request information and return a HTTP response. See 
those classes for more information if you would like, but also feel free to rely on the general practices
demonstrated in SampleDomain.

Note that SampleDomain is also demonstrating usage of the AuthUtils class, which does not really have any
deep magic knowledge - it just looks at the request instances and makes its decisions (like examining the 
cookie header and checking its list of known sessions)

See how the class and related method are "registered" in the class titled "atqa.TheRegister".  Please use 
the same patterns in your own code to register each new business need.

All this should keep your systems exceedingly simple, which will aid maintenance.
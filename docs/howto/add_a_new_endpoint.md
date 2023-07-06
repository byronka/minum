Adding a new endpoint
=====================

The anticipated pattern for this project is to split up the endpoints amongst the
classes.  Since most web applications consist of a large number of endpoints, it
follows that there will be many classes.  This will help keep complexity tamped
down.

To build a new endpoint, follow this guidance.

### Step 1 - Name and location

I've generally found that for something as hard as naming, it's best just to 
pick a bad name and bad location, and hold off until the end to make those 
choices.  Foo is always an excellent option.  Thankfully, Java is a 
strongly-typed language with good tooling options, so automatic renaming is 
easy and safe.  You are using a good IDE aren't you? Even though I love 
[Vim](https://www.vim.org/), I really recommend fully-fledged IDE's for 
serious work, like [Intellij](https://www.jetbrains.com/idea/)

### Step 2 - Setup the template

Here's a template for the most likely case - needing access to persisted data. I'm
including a method called `myNewEndpoint` which is what will handle the client request.

The full example is at 'src/test/minum/sampledomain/SampleDomain.java'

I'm going to heavily document this so you understand why each part is necessary, and
to explain some parts which aren't immediately apparent in the code. Please do pay
attention to all the comments, they are all contextual to this HOWTO.

Quick summary:

Endpoints in this class, `formEntry`, `testform`, and `sampleDomainIndex`.
Each has the same method signature: `Response methodName(Request r)`

```java

package minum.sampledomain;

public class SampleDomain {

    /* ****************** */
    /* Instance variables */
    /* ****************** */

    /*
        This object lets us deal with disk persistence for
        all the PersonName data. 
     */
    private final DatabaseDiskPersistenceSimpler<PersonName> ddps;
    
    /*
        This is important data for the endpoints.  It remains
        here in memory, but when we add to it, we must also remember
        to write to disk.  When we delete from this, we delete
        from disk.
     */
    private final List<PersonName> personNames;
    
    /*
        The AuthUtils are used to authenticate / authorize the user
     */
    private final AuthUtils auth;
    
    /*
        Each new PersonName gets a unique identifier.  Using an
        AtomicLong, we don't have to worry about multiple threads.
        The whole point of the Atomic classes is that they are
        thread safe and don't require synchronization, so they
        don't slow us down.
     */
    private final AtomicLong newPersonIndex;
    
    /*
        A TemplateProcessor is how we use templates.  See the
        constructor for details on usage. You should store
        templates in the template directory, at src/resources/templates
        
        You will note I use FileUtils.readTemplate, which looks into that
        directory.
        
        Summarizing: if you have a template file, it goes in the template
        directory, and if you have a static file like an image or css, that
        goes under static.
     */
    private final TemplateProcessor nameEntryTemplate;
    
    /*
        You may decide to keep non-template strings in the template directory,
        simply because it is easier to organize, and they won't be publicly 
        available - you will need to read them in.
     */
    private final String authHomepage;
    private final String unauthHomepage;

    /* ****************** */
    /* Constructor        */
    /* ****************** */
    
    public SampleDomain(DatabaseDiskPersistenceSimpler<PersonName> diskData, AuthUtils auth) {
        
        
        this.ddps = diskData;
        
        /*
            This is where we pull the data from disk, and only happens once - when
            this class is instantiated at system startup.
         */
        
        personNames = diskData.readAndDeserialize(PersonName.EMPTY);
        this.auth = auth;
        
        /*
            Initialize the AtomicLong.  We have to calculate the first index
            based on what has been read from disk.    
         */
        
        newPersonIndex = new AtomicLong(ddps.calculateNextIndex(personNames));
        nameEntryTemplate = TemplateProcessor.buildProcessor(FileUtils.readTemplate("sampledomain/name_entry.html"));
        authHomepage = FileUtils.readTemplate("sampledomain/auth_homepage.html");
        unauthHomepage = FileUtils.readTemplate("sampledomain/unauth_homepage.html");
    }

    /*
        Observe how in the following methods, everything must be explicitly
        done, there is no magic that will take care of things if you don't.    
        
        Authentication is done by passing the request object into the processAuth
        method, which will look at the data coming from the client - headers, mainly - 
        and calculate whether this is an authenticated request.
     */
    
    public Response formEntry(Request r) {
        final var authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return new Response(_401_UNAUTHORIZED);
        }
        final String names = personNames
                .stream().sorted(Comparator.comparingLong(PersonName::getIndex))
                .map(x -> "<li>" + StringUtils.safeHtml(x.getFullname()) + "</li>\n")
                .collect(Collectors.joining());

        return Response.htmlOk(nameEntryTemplate.renderTemplate(Map.of("names", names)));
    }

    /*
        Observe that as we get new data, we have to incorporate it in
        the in-memory data structure (in this case, a list), as well
        as writing it to disk.    
     */
    
    public Response testform(Request r) {
        final var authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return new Response(_401_UNAUTHORIZED);
        }

        final var nameEntry = r.body().asString("name_entry");

        final var newPersonName = new PersonName(newPersonIndex.getAndIncrement(), nameEntry);
        personNames.add(newPersonName);
        ddps.persistToDisk(newPersonName);
        return new Response(_303_SEE_OTHER, List.of("Location: formentry"));
    }

    public Response sampleDomainIndex(Request request) {
        final var authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return Response.htmlOk(unauthHomepage);
        } else {
            return Response.htmlOk(authHomepage);
        }

    }

}


```


### Step 3 - Initialize the object, register the endpoints

There could be different ways to do this, but at this time I am handling most
of this work in `TheRegister.java`.  Following is a boiled-down version.  As
you can see, it's plain old method calls.  We instantiate TheRegister with an
instance of WebFramework, which has a Context object.

```java

public class TheRegister {

    public TheRegister(WebFramework webFramework) {
        ...
    }

    public void registerDomains() {
        var auth = ...
        var sd = setupSampleDomain(auth);

        // sample domain stuff
        webFramework.registerPath(StartLine.Verb.GET, "formentry", sd::formEntry);
        webFramework.registerPath(StartLine.Verb.POST, "testform", sd::testform);

    }

    private SampleDomain setupSampleDomain(AuthUtils auth) {
        DatabaseDiskPersistenceSimpler<PersonName> sampleDomainDdps = webFramework.getDdps("names");
        return new SampleDomain(sampleDomainDdps, auth);
    }
}


```
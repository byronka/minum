HOWTO - Adding a new endpoint
=============================

The anticipated pattern for this project is to split up the endpoints amongst the
classes.  Since most web applications consist of a large number of endpoints, it
follows that there will be many classes.  This will help keep complexity tamped
down.

To build a new endpoint, follow this guidance.

### Step 1 - Name and location

I've generally found that for something as hard as naming, it's best just to 
pick a bad name and bad location, and hold off until the end to make those 
choices.  Foo is an excellent option.  Thankfully, Java is a 
strongly-typed language with good tooling options, so automatic moving and renaming is 
easy and safe.  You are using a good IDE aren't you? Even though I love 
[Vim](https://www.vim.org/), I really recommend fully-fledged IDE's like [Intellij](https://www.jetbrains.com/idea/) for 
serious work. 

### Step 2 - Setup the template

Here's a template for the most likely case - needing access to persisted data. I'm
including a method called `myNewEndpoint` which is what will handle the client request.

The full example is at `src/test/minum/sampledomain/SampleDomain.java`

I'm going to heavily document this so you understand why each part is necessary, and
to explain some parts which aren't immediately apparent in the code. Please do pay
attention to all the comments, they are all contextual to this HOWTO.

Quick summary:
--------------

* Endpoints in this class: `formEntry`, `testform`, and `sampleDomainIndex`.
* Each has the same method signature: `Response methodName(Request r)`

```java

package minum.sampledomain;

import com.renomad.minum.utils.FileUtils;

public class SampleDomain {

    /* ****************** */
    /* Instance variables */
    /* ****************** */

    /*
        This object lets us deal with disk persistence for
        all the PersonName data. 
     */
    private final Db<PersonName> db;

    /*
        The AuthUtils are used to authenticate / authorize the user
     */
    private final AuthUtils auth;

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
        available for GET requests.
     */
    private final String authHomepage;
    private final String unauthHomepage;

    /* ****************** */
    /* Constructor        */
    /* ****************** */

    public SampleDomain(Db<PersonName> db, AuthUtils auth, Context context) {
        this.db = db;
        this.auth = auth;
        FileUtils fileUtils = new FileUtils(context.getLogger(), context.getConstants());
        String nameEntryTemplateString = fileUtils.readTextFile("src/test/webapp/templates/sampledomain/name_entry.html");
        nameEntryTemplate = TemplateProcessor.buildProcessor(nameEntryTemplateString);
        authHomepage = fileUtils.readTextFile("src/test/webapp/templates/sampledomain/auth_homepage.html");
        unauthHomepage = fileUtils.readTextFile("src/test/webapp/templates/sampledomain/unauth_homepage.html");
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
            return new Response(CODE_401_UNAUTHORIZED);
        }
        final String names = db
                .values().stream().sorted(Comparator.comparingLong(PersonName::getIndex))
                .map(x -> "<li>" + StringUtils.safeHtml(x.getFullname()) + "</li>\n")
                .collect(Collectors.joining());

        return Response.htmlOk(nameEntryTemplate.renderTemplate(Map.of("names", names)));
    }

    public Response testform(Request r) {
        final var authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return new Response(CODE_401_UNAUTHORIZED);
        }

        final var nameEntry = r.body().asString("name_entry");

        final var newPersonName = new PersonName(0L, nameEntry);
        db.write(newPersonName);
        return new Response(CODE_303_SEE_OTHER, Map.of("Location","formentry"));
    }

    /**
     * This is an example of a homepage for a domain.  Here we examine
     * whether the user is authenticated.  If not, we request them to
     * log in.  If already, then we show some features and the log-out link.
     */
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
        webFramework.registerPath(StartLine.Method.GET, "formentry", sd::formEntry);
        webFramework.registerPath(StartLine.Method.POST, "testform", sd::testform);

    }

    private SampleDomain setupSampleDomain(AuthUtils auth) {
        Db<PersonName> sampleDomainDb = webFramework.getDb("names");
        return new SampleDomain(sampleDomainDb, auth);
    }
}


```
Maven
=====

These are files necessary for publishing Minum to the Maven Central repository

How to deploy to Maven Central
------------------------------

Run `make mvnprep`

There will now be a file `out/bundle.zip`, which you will use in
the process explained [here](https://central.sonatype.org/publish/publish-portal-upload/)

In case the web page is down, here's the gist of it:

1. Once bundle.zip has been produced, log into [OSSRH](https://central.sonatype.com/publishing/namespaces), and select Publish Component.
2. For "Deployment Name", enter: com.renomad:minum:VERSION_GOES_HERE
3. For "Description", enter: A minimalist web framework
4. Select the button "Choose File"
5. Click "Publish Component"

Deployment checklist
--------------------
- [ ] local testing with new version number
- [ ] ensure release notes are properly updated
- [ ] spot-check test logs
- [ ] confirm howtos
- [ ] uncomment linting tools in maven-compiler-plugin and examine the output during build.  Note this will be
     full of false positives, so examine with a critical eye.
- [ ] adjust code in sample programs as needed, run their test programs 
- [ ] review generated bundle.jar, particularly its pom and manifest
- [ ] update versions in quick start, tutorial, and example projects
- [ ] squash the changes to a single commit
- [ ] generate and examine reports: site page, pitest report, javadoc
- [ ] publish to Maven central
- [ ] push to Github, add Release

Gnupg - GNU privacy guard
-------------------------

Gnupg [gpg](https://gnupg.org/) is used to sign the bundle for shipping to Maven central. Its entire
configuration directory is encrypted and stored here, as `gnupg.tar.gz.encrypted`.

Occasionally it will be necessary to extend the expiration date for the private key,
presuming it is still 

The passphrase to decrypt this file is the same as the one used when creating a signed bundle.

To encrypt the directory:

```shell
gpg --output gnupg.tar.gz.encrypted --symmetric --cipher-algo AES256 gnupg.tar.gz
```

To decrypt the directory:

```shell
gpg --output gnupg.tar.gz --decrypt gnupg.tar.gz.encrypted
```

to untar the result:

```shell
tar zxf gnupg.tar.gz
```
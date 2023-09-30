Maven
=====

These are files necessary for publishing Minum to the Maven Central repository

How to deploy to Maven Central
------------------------------

Run `make mvnprep`

There will now be a file `out/bundle.jar`, which you will use in
the process explained [here](https://central.sonatype.org/publish/publish-manual/#bundle-creation)

In case the web page is down, here's the gist of it:

1. Once bundle.jar has been produced, log into [OSSRH](https://s01.oss.sonatype.org/), and select 
Staging Upload in the Build Promotion menu on the left.
2. From the Staging Upload tab, select Artifact Bundle from the Upload Mode dropdown.
3. Then click the Select Bundle to Upload button, and select the bundle you just created.
4. Click the Upload Bundle button. If the upload is successful, a staging repository will be
 created, and you can proceed with [releasing](https://central.sonatype.org/publish/release/).

Deployment checklist
--------------------
- [ ] review logs
- [ ] directory review
- [ ] documentation review
- [ ] spot-check code
- [ ] generate reports: site page, pitest report, javadoc
- [ ] generate and review javadocs
- [ ] add tests
- [ ] confirm howtos
- [ ] confirm quick start
- [ ] confirm patterns in example project
- [ ] review generated bundle.jar, particularly its pom and manifest
- [ ] add Github release
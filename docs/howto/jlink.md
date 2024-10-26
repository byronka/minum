# Creating Application images with Jlink and Minum

### Prerequisites

- an application using Minum
- a `module-info.java` file in the application
- a main class (a class containing a `public static void main(String[] args)`) file in the application

example `module-info.java`: 

```java
module example.module {
  requires com.renomad.minum;
}
```

Example main class: 

```java
package com.example

public class MainClass {

  public static void main(String[] arg) {
    // Start the system
    FullSystem fs = FullSystem.initialize();

    // Register some endpoints
    fs.getWebFramework().registerPath(
        StartLine.Method.GET,
        "",
        request -> Response.htmlOk("<p>Hi there world!</p>"));

    fs.block();
  }
}
```

## 1. Copy all your dependencies to `target/modules`

Add the following plugin configuration to your application's `pom.xml` file.
```xml
<plugin>
    <artifactId>maven-dependency-plugin</artifactId>
    <executions>
        <execution>
            <id>copy-modules</id>
            <phase>package</phase>
            <goals>
                <goal>copy-dependencies</goal>
            </goals>
            <configuration>
                <outputDirectory>${project.build.directory}/modules</outputDirectory>
                <includeScope>runtime</includeScope>
            </configuration>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <configuration>
        <outputDirectory>${project.build.directory}/modules</outputDirectory>
    </configuration>
</plugin>
```

This configures your build to place the application jar and all runtime dependencies to `target/modules`.

## 2. Use Jlink to create a slim Java Runtime (JRT) with only the modules required to run the application

We use the `--add-modules <your.module>` flag to tell jlink to discover all the modules required by our application to build the java runtime. `--module-path target/modules` tells jlink where it should look for non-JDK modules

```shell
jlink --add-modules example.module --module-path target/modules --output ./target/jrt
```

## 3. Run the application

With the new runtime created in `target/jrt` we can execute our application using the java command in `target/jrt/bin/java`. To start the application, execute your module with the form `java --module <your.module>/<your.main.class>`

```shell
./target/jrt/bin/java --module example.module/com.example.MainClass
```

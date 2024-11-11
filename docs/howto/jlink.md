# Creating Application images with Jlink and Minum

## 1. Copy all your dependencies to `target/modules`

The following plugin configuration will copy the application jar and all the runtime dependencies to `target/modules`.
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

There is a practical example of this in the [pom file of memoria_project](https://github.com/byronka/memoria_project/blob/master/pom.xml) .
Search that file for "maven-dependency-plugin".

## 2. Use Jlink to create a slim Java Runtime (JRT) with only the modules required to run the application

```shell
jlink --add-modules <your.module> --module-path target/modules --output /target/jrt
```

See the "jlink" target in the [Makefile for memoria_project](https://github.com/byronka/memoria_project/blob/master/Makefile) to
see a practical example of this.  To see it in action, run `make jlink` after cloning that project.

## 3. Run the application

```shell
./target/jrt/bin/java -m <your.module>/<main.class>
```

See the "runjlink" target in the [Makefile for memoria_project](https://github.com/byronka/memoria_project/blob/master/Makefile) for
a practical example.  To see if in action, run `make runjlink`
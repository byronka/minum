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
## 2. Use Jlink to create a slim Java Runtime (JRT) with only the modules required to run the application

```shell
jlink --add-modules <your.module> --module-path target/modules --output /target/jrt
```

## 3. Run the application

```shell
./target/jrt/bin/java -m <your.module>/<main.class>
```

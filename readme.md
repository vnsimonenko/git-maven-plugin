# git-maven-plugin Plugin #
The git-maven-plugin Plugin download project from github and install artifacts to a local repository.

### Goals ###
git-maven-plugin:git-maven-plugin:git - download project from github and execute maven commands for it.

### Rules ###

* if the project already exists and is not changed, the project will not be loaded.

* If the *working directory* is not specified, the default value is assigned according to:
${project.build.directory}/git-maven-plugin-working-dir/[last part of path of scm].
example:
```
<scm>scm:git:https://github.com/projects/akka-stream</scm>
workingDir = ${project.basedir}/git-maven-plugin-working-dir/akka-stream/
```

* If dependencies are not installed in the local repository you need to perform at the beginning:
```
mvn git-maven-plugin:git-maven-plugin:git
```

### Example: ###
```
<plugin>
    <groupId>git-maven-plugin</groupId>
    <artifactId>git-maven-plugin</artifactId>
    <version>1.0</version>
    <executions>
        <execution>
            <goals>
                <goal>git</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <repositories>
            <repository>
                <scm>scm:git:https://github.com/projetcs/CollectionManager</scm>
                <scmVersionType>revision</scmVersionType>
                <scmVersion>master</scmVersion>
                <command>clean package install -DskipTests</command>
            </repository>
            <repository>
                <scm>scm:git:https://github.com/projects/akka-stream</scm>
                <workingDir>${project.basedir}/target/akka-stream/</workingDir>                
            </repository>
        </repositories>
    </configuration>
</plugin>
```
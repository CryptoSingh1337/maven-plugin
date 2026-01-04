# Maven plugins

This repository contains a collection of Maven plugins that can be used to enhance your build process. 
Each plugin is designed to perform specific tasks and can be easily integrated into your Maven projects.

## Available Plugins
- **Drools Spreadsheet Plugin**: End-to-end tooling for spreadsheet-based rules, including: 
  - Spreadsheet structure and schema validation 
  - Ruleset consistency and semantic validation 
  - `kmodule.xml` validation and KIE configuration checks
  - Automated generation of DRL from spreadsheets

## Installation
To use any of the plugins in this repository, you need to add the plugin dependency to your

- Clone specific plugin from the repository
```shell
rm -rf drools-plugin
mkdir drools-plugin
cd drools-plugin

# Init empty repo
git init

# Add remote (PAT included)
git remote add origin https://github.com/CryptoSingh1337/maven-plugin.git

# Enable sparse checkout
git config core.sparseCheckout true

# Specify the folder you want
echo "drools-spreadsheet-maven-plugin/" >> .git/info/sparse-checkout

# Pull only that directory
git pull origin master
```

- Build the plugin using Maven
```shell
cd drools-spreadsheet-maven-plugin

mvn -B -U -e clean install -DskipTests
```

## Usage
To use a plugin in your Maven project, add the following configuration to your `pom.xml`
```xml
<plugin>
    <groupId>pro.saransh</groupId>
    <artifactId>drools-spreadsheet-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <id>validate-all</id>
            <phase>process-classes</phase>
            <goals>
                <goal>validate-all</goal>
            </goals>
            <configuration>
                <resourcesDir>${project.basedir}/src/main/resources</resourcesDir>
                <poolSize>4</poolSize>
                <validators>table,kmodule</validators>
            </configuration>
        </execution>
        <execution>
            <id>generate-drl</id>
            <phase>process-classes</phase>
            <goals>
                <goal>generate-drl</goal>
            </goals>
            <configuration>
                <resourcesDir>${project.basedir}/src/main/resources</resourcesDir>
                <outputDir>${project.build.directory}</outputDir>
                <poolSize>4</poolSize>
            </configuration>
        </execution>
    </executions>
</plugin>
```
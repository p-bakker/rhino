## Contributing PRs

To submit a new PR, please use the following process:

* Ensure that your entire build passes "./gradlew check". This will include
code formatting and style checks and runs the tests.
* Please write tests for what you fixed, unless you can show us that existing
tests cover the changes. Use existing tests, such as those in 
"testsrc/org/mozilla/javascript/tests", as a guide.
* If you fixed ECMAScript spec compatibility, take a look at test262.properties and see
if you can un-disable some tests.
* Push your change to GitHub and open a pull request.
* Please be patient as Rhino is only maintained by volunteers and we may need
some time to get back to you.
* Thank you for contributing!

### Code Formatting

Code formatting was introduced in 2021. The "spotless" plugin will fail your
build if you have changed any files that have not yet been reformatted. 
Please use "spotlessApply" to reformat the necessary files.

If you are the first person to touch a big file that spotless wants to make
hundreds of lines of changes to, please try to put the reformatting changes
alone into a single Git commit so that we can separate reformatting changes
from more substantive changes.

## Building

### How to Build

Rhino builds with `Gradle`. Here are some useful tasks:
```
./gradlew jar
```
Build and create `Rhino` jar in the `buildGradle/libs` directory.
```
git submodule init
git submodule update
./gradlew test
```
Build and run all the tests, including the official [ECMAScript Test Suite](https://github.com/tc39/test262).
See [Running tests](testsrc/README.md) for more detailed info about running tests.
```
./gradlew testBenchmark
```
Build and run benchmark tests.

## Running

Rhino can run as a stand-alone interpreter from the command line:
```
java -jar buildGradle/libs/rhino-1.7.12.jar -debug -version 200
Rhino 1.7.9 2018 03 15
js> print('Hello, World!');
Hello, World!
js>
```
There is also a "rhino" package for many Linux distributions as well as Homebrew for the Mac.
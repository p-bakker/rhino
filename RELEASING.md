## Releasing and publishing new version

1. Ensure all tests are passing
2. Remove `-SNAPSHOT` from version in `gradle.properties` in project root folder
3. Create file `gradle.properties` in `$HOME/.gradle` folder with following properties. Populate them with maven repo credentials and repo location.
```
mavenUser=
mavenPassword=
mavenSnapshotRepo=
mavenReleaseRepo=
```

4. Run `Gradle` task to publish artifacts to Maven Central.
```
./gradlew publish
```
5. Increase version and add `-SNAPSHOT` to it in `gradle.properties` in project root folder.
6. Push `gradle.properties` to `GitHub`
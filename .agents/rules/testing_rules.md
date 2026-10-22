# Testing rules

- Run `./gradlew test assembleRelease` after every change; report the test count.
- New pure logic gets a unit test in `app/src/test/java/com/berkbb/otaghome/`.
- `org.json` in unit tests comes from the `org.json:json` test dependency.
- UI changes that cannot be unit-tested are checked on a TV only when the user asks for it.

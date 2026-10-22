# Skill: verify a change

1. `./gradlew test assembleRelease --console=plain`
2. Read the totals from `app/build/test-results/testReleaseUnitTest/*.xml` (tests, failures, errors).
3. Fix any failure before reporting; after three failed attempts on the same problem, stop and
   ask the user.
4. For a release, run `./package.sh` and check the signing line it prints.

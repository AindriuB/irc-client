# Releasing to Maven Central

The coordinates are `io.github.aindriub:irc-client`. That namespace is verified
against the GitHub account, so there is no domain to prove ownership of.

## One-off setup

1. **Central Portal account.** Register at https://central.sonatype.com and claim
   the `io.github.aindriub` namespace. Verification is a GitHub check, so it needs
   the same account that owns this repository.

2. **A signing key.** Central requires every artifact to be signed.

   ```bash
   gpg --gen-key                       # RSA 4096, no expiry or a long one
   gpg --list-keys --keyid-format short
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

   The key has to be on a public keyserver before the upload is validated, and
   propagation is not instant. Send it well before you need it.

3. **Credentials in `~/.m2/settings.xml`.** The portal issues a user token rather
   than using the account password.

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>TOKEN_USERNAME</username>
         <password>TOKEN_PASSWORD</password>
       </server>
     </servers>
     <profiles>
       <profile>
         <id>gpg</id>
         <activation><activeByDefault>true</activeByDefault></activation>
         <properties>
           <gpg.keyname>KEY_ID</gpg.keyname>
         </properties>
       </profile>
     </profiles>
   </settings>
   ```

   `settings.xml` holds secrets. It belongs in `~/.m2`, never in the repository.

## Each release

```bash
# 1. Confirm master is green on its own terms first.
mvn -B verify

# 2. Set the release version, commit and tag.
mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
git commit -am "Release 1.0.0"
git tag -a v1.0.0 -m "Release 1.0.0"

# 3. Build, sign and upload. This runs the javadoc and sources jars and the
#    signing, none of which the ordinary build does.
mvn -B -Prelease deploy

# 4. Confirm the deployment at https://central.sonatype.com/publishing.
#    autoPublish is off, so nothing goes public until you press the button.
#    Artifacts appear on Central within roughly 30 minutes, and in the search
#    index within a few hours.

# 5. Open the next development version and push.
mvn versions:set -DnewVersion=1.0.1-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Back to snapshot"
git push && git push --tags
```

## Before pressing publish

A release cannot be withdrawn or replaced. Check:

- `mvn -Prelease verify` produces `irc-client-1.0.0.jar`, `-sources.jar`,
  `-javadoc.jar` and an `.asc` for each.
- The jar's class files are major version 52, so Java 8 consumers can load it:
  `javap -v -cp target/classes io.github.aindriub.irc.client.Client | grep major`
- `LICENSE` and `NOTICE` are inside the jar.
- The version in the README's dependency snippet matches.

## What the version number promises

1.0.0 under semantic versioning means the public API is a commitment.
API-breaking changes need 2.0.0, so anything that changes a public signature or
a documented behaviour belongs before a release, not after.

# Releasing to Maven Central

The coordinates are `io.github.aindriub:irc-client`. That namespace is verified
against the GitHub account, so there is no domain to prove ownership of.

## One-off setup

1. **Central Portal account.** Register at https://central.sonatype.com and claim
   the `io.github.aindriub` namespace. Verification is a GitHub check, so it needs
   the same account that owns this repository.

2. **A signing key.** Central requires every artifact to be signed, and the
   signature is checked against a public keyserver.

   ```bash
   gpg --full-generate-key       # RSA, 4096 bits, no expiry or a long one
   gpg --list-secret-keys --keyid-format=long
   ```

   The output names the key. The long hex after `rsa4096/` is the key id:

   ```
   sec   rsa4096/A1B2C3D4E5F6A7B8 2026-09-22 [SC]
         1234567890ABCDEF1234567890ABCDEF12345678
   uid                 [ultimate] Andrew Bannister <you@example.com>
   ```

   Publish it, to more than one keyserver — they do not all reliably sync, and
   the upload is validated against whichever Central happens to ask:

   ```bash
   gpg --keyserver keyserver.ubuntu.com --send-keys A1B2C3D4E5F6A7B8
   gpg --keyserver keys.openpgp.org     --send-keys A1B2C3D4E5F6A7B8
   ```

   **Send it well before you need it.** Propagation is not instant, and an
   upload whose key is not yet findable is rejected with an error that does not
   say so clearly. Confirm it landed before releasing:

   ```bash
   gpg --keyserver keyserver.ubuntu.com --recv-keys A1B2C3D4E5F6A7B8
   ```

   Back up the key and the revocation certificate somewhere other than the
   machine that generated them. Losing the key means every later release has to
   be signed by a different one; losing the revocation certificate means a
   compromised key cannot be withdrawn.

3. **Credentials in `~/.m2/settings.xml`.** The portal issues a user token
   rather than using the account password. Generate one under your Central
   account, then:

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>TOKEN_USERNAME</username>
         <password>TOKEN_PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```

   `settings.xml` holds secrets. It belongs in `~/.m2`, never in this
   repository. The passphrase does **not** go in it — see below.

## Signing

The passphrase goes in the environment, not on the command line and not in
`settings.xml`:

```bash
export MAVEN_GPG_PASSPHRASE='your passphrase'
```

`-Dgpg.passphrase=` also works but is deprecated by the plugin, and it puts the
passphrase in your shell history and in the process list where any other user
on the machine can read it.

The key is chosen with `gpg.keyname`, which is not secret. If it is omitted, gpg
signs with whichever key it considers the default — fine with one key in the
keyring, wrong as soon as there are two:

```bash
mvn -Prelease verify -Dgpg.keyname=A1B2C3D4E5F6A7B8
```

That produces, and signs, four files:

```
target/irc-client-1.0.0.jar          + .asc
target/irc-client-1.0.0-sources.jar  + .asc
target/irc-client-1.0.0-javadoc.jar  + .asc
target/irc-client-1.0.0.pom          + .asc
```

Central rejects the upload if any one of the four signatures is missing. Check
them locally first, which is much faster than finding out after an upload:

```bash
ls target/*.asc
gpg --verify target/irc-client-1.0.0.jar.asc target/irc-client-1.0.0.jar
```

A good signature prints `Good signature from` and the key's name.

### If signing fails

| Symptom | Cause |
| --- | --- |
| `gpg: signing failed: Inappropriate ioctl for device` | gpg wants to prompt for the passphrase but has no terminal. Set `MAVEN_GPG_PASSPHRASE`, or add `pinentry-mode loopback` to `~/.gnupg/gpg.conf`. |
| `gpg: skipped "...": No secret key` | The `gpg.keyname` does not match a key in the keyring. Check `gpg --list-secret-keys --keyid-format=long`. |
| `gpg: signing failed: Bad passphrase` | The passphrase is wrong, or a stale `gpg-agent` has cached an old one. `gpgconf --kill gpg-agent` and retry. |
| Upload rejected, key not found | The key has not reached the keyserver Central queried. Wait, and send it to more than one. |

## Each release

Two routes. **CI is the better one**: the key lives in GitHub secrets rather
than on a laptop, and the release is reproducible by anyone with access.

### Via CI

One-off, add four repository secrets under Settings → Secrets → Actions:

| Secret | What it is |
| --- | --- |
| `GPG_PRIVATE_KEY` | `gpg --armor --export-secret-keys A1B2C3D4E5F6A7B8` |
| `GPG_PASSPHRASE` | the passphrase for that key |
| `CENTRAL_TOKEN_USERNAME` | portal user token, username half |
| `CENTRAL_TOKEN_PASSWORD` | portal user token, password half |

Then per release:

```bash
mvn -B verify                                                   # green first
mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
git commit -am "Release 1.0.0"
git tag -a v1.0.0 -m "Release 1.0.0"
git push && git push --tags                                     # the tag starts it
```

The `release` workflow checks the tag matches the pom version and refuses a
snapshot, builds and signs, confirms every artifact has a signature, then
uploads. Run it from the Actions tab with `dry_run` first to build and sign
without uploading.

Then reopen development:

```bash
mvn versions:set -DnewVersion=1.0.1-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Back to snapshot" && git push
```

### By hand

```bash
export MAVEN_GPG_PASSPHRASE='your passphrase'
mvn -B verify
mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
mvn -B -Prelease deploy -Dgpg.keyname=A1B2C3D4E5F6A7B8
git commit -am "Release 1.0.0" && git tag -a v1.0.0 -m "Release 1.0.0"
git push && git push --tags
mvn versions:set -DnewVersion=1.0.1-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Back to snapshot" && git push
```

Either way the upload only *stages* the deployment: `autoPublish` is off, so
nothing is public until you confirm it at
https://central.sonatype.com/publishing. Artifacts appear on Central within
roughly 30 minutes of confirming, and in the search index within a few hours.

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

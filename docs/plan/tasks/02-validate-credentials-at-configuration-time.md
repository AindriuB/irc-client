# 02 — Validate credentials at configuration time, and prove the log never sees them

**Repo:** /srv/dev/projects/irc-client
**Depends on:** 01
**Owns:**
- src/main/java/io/github/aindriub/irc/client/configuration/ClientConfigurationBuilder.java
- src/main/java/io/github/aindriub/irc/client/bot/IRCBotBuilder.java
- src/test/java/io/github/aindriub/irc/client/configuration/ClientConfigurationBuilderTest.java
- src/test/java/io/github/aindriub/irc/client/bot/IRCBotBuilderSecretTest.java (new)
- src/test/java/io/github/aindriub/irc/client/handler/RegistrationSecretLogTest.java (new)

## Goal
A password the PASS command will refuse currently builds fine and then fails inside
RegistrationHandler.channelActive on every reconnect, forever. Make the builder
setters apply the same rules the commands apply, so a bad secret throws
IllegalArgumentException once, at configuration time. Then pin down, with a
log-capture test, that a registration-time failure never writes the secret to any
log. A stricter check is acceptable as a bug fix in patch 1.2.1; do not bump the version.

## Context
- src/main/java/io/github/aindriub/irc/client/configuration/ClientConfigurationBuilder.java:101-133 — password(String), sasl(String, String)
- src/main/java/io/github/aindriub/irc/client/bot/IRCBotBuilder.java:53-59 — password passthrough (IRCBotBuilder has no sasl passthrough)
- src/main/java/io/github/aindriub/irc/client/command/Pass.java:14 and command/Authenticate.java:70-72 — the rules to mirror: password uses requireParam; sasl username uses requireParam; sasl password uses requireText
- src/main/java/io/github/aindriub/irc/client/configuration/RegistrationConfiguration.java:68-81,112-118 — public setters that bypass the builder; saslUsername null means "use the nick"
- src/main/java/io/github/aindriub/irc/client/handler/RegistrationHandler.java:70-83,300-307 — channelActive writes new Pass(...); exceptionCaught
- src/test/java/io/github/aindriub/irc/client/impl/ConnectionEventsTest.java:23-36 — the logback AppenderBase capture pattern to copy
- src/test/java/io/github/aindriub/irc/client/handler/RegistrationHandlerTest.java — how the handler is driven (EmbeddedChannel or stub)

## Acceptance
- [ ] `ClientConfigurationBuilder.password(p)` calls `IRCText.requireParam(p, "password")` when `p != null`. `password(null)` still succeeds and leaves the password unset, as it does today
- [ ] `sasl(u, p)` keeps throwing NullPointerException for a null password (existing behaviour), then calls `IRCText.requireText(p, "sasl password")`. When `u != null` it calls `IRCText.requireParam(u, "sasl username")`. Validation runs before any field is set or the sasl capability is added
- [ ] Javadoc on `password`, `sasl` and `IRCBotBuilder.password` gains `@throws IllegalArgumentException` stating the rule (no whitespace, no leading ':', no CR/LF/NUL) and that the value is not included in the message
- [ ] No public signature changes. `git diff --stat` touches only the Owns files
- [ ] ClientConfigurationBuilderTest: `password("PASS oauth:tok123")` throws IAE and the message does not contain `"tok123"`; `password(":x")` throws; `sasl("a b", "pw")` and `sasl("u", "p\r\nQUIT")` throw without echoing the value; `sasl(null, "pw with space")` succeeds
- [ ] IRCBotBuilderSecretTest: `IRCBot.builder()...password("PASS oauth:tok123")` throws IAE at the setter, before `build()`, and the message does not contain `"tok123"`
- [ ] RegistrationSecretLogTest attaches an AppenderBase to the ROOT logger at TRACE. It builds a RegistrationConfiguration directly through `setPassword("PASS oauth:tok123")`, bypassing the builder, and drives RegistrationHandler.channelActive until it fails. It asserts that the failure happened, that no captured event's formatted message or throwable chain (messages of every cause) contains `"tok123"`, and that the thrown exception's message does not contain it either. Do the same for a SASL password containing `\n` if the handler path is reachable in the same way
- [ ] `mvn verify` passes, coverage gate included

## Out of scope
- IRCText message wording (task 01; this task consumes it)
- Validating in RegistrationConfiguration's public setters. Leave the POJO setters permissive, and do not change RegistrationConfiguration.java
- Changing RegistrationHandler's reconnect/give-up behaviour for permanent failures
- Adding an IRCBotBuilder.sasl passthrough (new API)
- Version bump, PLAN.md, HISTORY.md

## Added from review of 01
Owns also: src/main/java/io/github/aindriub/irc/client/command/Join.java and its test.
`Join.toString()` includes channel keys, so any caller that logs a keyed Join logs the keys. The library itself logs only getChannels. Redact keys in toString the same way Pass and Authenticate redact theirs (e.g. `JOIN #a,#b <2 keys redacted>`); render() must still send them. Add a test asserting the key is absent from toString and present in render().

# 04 — Forget a channel in BasicIRCClient when we are kicked from it

**Repo:** .
**Depends on:** 01
**Owns:**
- src/main/java/io/github/aindriub/irc/client/impl/BasicIRCClient.java
- src/test/java/io/github/aindriub/irc/client/impl/ReconnectTest.java
- src/test/java/io/github/aindriub/irc/client/impl/BasicIRCClientTest.java

## Goal
BasicIRCClient's `joined` map only learns from outbound JOIN/PART, so after a
KICK the next reconnect rejoins a channel we were thrown out of. The client must
watch inbound KICKs aimed at its own nick and drop the channel, comparing nicks and
channel names with the server's CASEMAPPING. It also exposes that mapping so the
bot (task 06) can use it whether or not channel tracking is on.

## Context
- src/main/java/io/github/aindriub/irc/client/impl/BasicIRCClient.java:28, 46-64 — `joined` map and `track()`
- src/main/java/io/github/aindriub/irc/client/impl/AbstractClient.java:149-210 — `configurePipeline`; the IRC message decoder is only added when `configuration.getMessageHandlers()` is non-empty
- src/main/java/io/github/aindriub/irc/client/event/MessageListener.java:44-46, 84 — how KICK is dispatched
- src/main/java/io/github/aindriub/irc/client/handler/RegistrationHandler.java:54-80, 260-267 — nick may change during registration; RPL_WELCOME param 0 is the nick actually in use; NICK from self changes it later
- src/main/java/io/github/aindriub/irc/client/state/CaseMapping.java (task 01) — `forToken`, `fold`, `equals`
- src/test/java/io/github/aindriub/irc/client/testsupport/StubIRCServer.java — `push(String)` to deliver server lines
- src/test/java/io/github/aindriub/irc/client/impl/ReconnectTest.java — existing rejoin tests to extend

## Acceptance
- [ ] Inbound `:op!u@h KICK #chan <self> :reason` removes `#chan` from `getJoinedChannels()`; after a reconnect no JOIN for `#chan` is sent (test in ReconnectTest against StubIRCServer)
- [ ] A KICK of another nick leaves the channel in `getJoinedChannels()`
- [ ] Self is the nick from RPL_WELCOME (falling back to the configured nick) and follows NICK changes by self: a KICK of the collision-retry nick `nick_` drops the channel
- [ ] With ISUPPORT `CASEMAPPING=rfc1459`, KICK of `#Chan[x]` targeting `NICK{1}` drops a channel joined as `#chan{x}` by `nick[1]`; with `CASEMAPPING=ascii` the channel is kept
- [ ] New javadoc'd `public CaseMapping getCaseMapping()` on BasicIRCClient returns RFC1459 before ISUPPORT, the advertised mapping after, ASCII for an unrecognised value
- [ ] Inbound KICK handling works when the configuration has no message handlers, does not add entries to the caller's `ClientConfiguration.getMessageHandlers()` list (size is identical before and after `connect()`), and runs before any configured message handler (test: a MessageListener in the config sees the channel already absent from `getJoinedChannels()` inside `onKick`)
- [ ] Outbound JOIN/PART tracking keys channels with the current case mapping (JOIN `#A` then PART `#a` leaves nothing joined)
- [ ] No existing public/protected signature changed; `mvn verify` passes

## Out of scope
- Auto-rejoin after kick and any `bot/` change (task 06)
- ChannelStateTracker's own KICK handling (already exists; task 01 owns it)
- Changes to AbstractClient beyond what BasicIRCClient can do by overriding

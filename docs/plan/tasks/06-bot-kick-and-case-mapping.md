# 06 — Add onKick, auto-rejoin and CASEMAPPING-aware self checks to the bot

**Repo:** .
**Depends on:** 01, 04, 05
**Owns:**
- src/main/java/io/github/aindriub/irc/client/bot/IRCBot.java
- src/main/java/io/github/aindriub/irc/client/bot/IRCBotBuilder.java
- src/main/java/io/github/aindriub/irc/client/bot/BotListener.java
- src/test/java/io/github/aindriub/irc/client/bot/IRCBotTest.java

## Goal
Bots cannot react to KICKs through BotListener and have no opt-in to rejoin after
one. Add `onKick`, `autoRejoinAfterKick`, and make the bot's self-nick check use
the server's case mapping. Depends on 05 only because both own IRCBot, the builder
and IRCBotTest.

## Context
- src/main/java/io/github/aindriub/irc/client/bot/BotListener.java — existing default no-op method style
- src/main/java/io/github/aindriub/irc/client/bot/IRCBot.java:269-383 — Router; add an `onKick` override (MessageListener.onKick at src/main/java/io/github/aindriub/irc/client/event/MessageListener.java:84)
- src/main/java/io/github/aindriub/irc/client/bot/IRCBot.java:385-387 — `isFromSelf`
- src/main/java/io/github/aindriub/irc/client/impl/BasicIRCClient.java — `getCaseMapping()` (task 04); use it rather than ChannelStateTracker, since the tracker is null when `trackChannelState(false)`
- src/main/java/io/github/aindriub/irc/client/bot/IRCBot.java:179-181 — `join()`, which goes through `sendCommand` and so re-tracks the channel for reconnect

## Acceptance
- [ ] `BotListener.onKick(IRCBot bot, String channel, String kicked, String by, String reason)` is a javadoc'd default no-op; the Router calls it (via `safely`) for every inbound KICK, with reason null when absent
- [ ] `IRCBotBuilder.autoRejoinAfterKick(boolean)` (javadoc'd, default false). When true, a KICK of self sends `JOIN #chan` and afterwards `bot.getClient().getJoinedChannels()` contains `#chan`; a KICK of another nick sends no JOIN; when false no JOIN is sent
- [ ] `isFromSelf` uses `client.getCaseMapping().equals(...)`: under default RFC1459, a PRIVMSG from `Bot[1]` when the bot's nick is `bot{1}` is ignored as self; under `CASEMAPPING=ascii` it is delivered
- [ ] The KICK self check uses the same comparison; all of the above holds with `trackChannelState(false)`
- [ ] `rg equalsIgnoreCase src/main/java/io/github/aindriub/irc/client/bot` returns nothing; no existing public/protected signature changed; `mvn verify` passes

## Out of scope
- BasicIRCClient's joined-map handling (task 04)
- Rejoin delay/backoff, or rejoining keyed channels with their key
- ChannelUser equality; command-name lower-casing in IRCBotBuilder.command

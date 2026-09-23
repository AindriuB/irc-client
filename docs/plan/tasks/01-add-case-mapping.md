# 01 — Add CaseMapping and apply it to channel state

**Repo:** .
**Depends on:** none
**Owns:**
- src/main/java/io/github/aindriub/irc/client/state/CaseMapping.java
- src/main/java/io/github/aindriub/irc/client/state/ServerSupport.java
- src/main/java/io/github/aindriub/irc/client/state/ChannelStateTracker.java
- src/main/java/io/github/aindriub/irc/client/state/ChannelState.java
- src/main/java/io/github/aindriub/irc/client/state/ChannelUser.java
- src/test/java/io/github/aindriub/irc/client/state/**

## Goal
Nick and channel comparisons currently use Java's `toLowerCase`/`equalsIgnoreCase`,
which is neither what IRC servers do nor safe outside ASCII. Introduce a public
`CaseMapping` enum driven by the server's ISUPPORT `CASEMAPPING` token and use it
for every nick/channel comparison in the `state` package. Later tasks (04, 06)
consume the enum from outside the package.

## Context
- src/main/java/io/github/aindriub/irc/client/state/ServerSupport.java:47-60 — `apply()` parses ISUPPORT tokens; add `getCaseMapping()` alongside `get`/`supports` (133-141)
- src/main/java/io/github/aindriub/irc/client/state/ChannelStateTracker.java:65 — `getServerSupport()`; comparison sites at 306 (`isSelf`) and 314 (`key`)
- src/main/java/io/github/aindriub/irc/client/state/ChannelState.java:25 (package-private ctor), 115 (`key`) — the tracker creates ChannelState, so it can hand the mapping in
- src/main/java/io/github/aindriub/irc/client/state/ChannelUser.java:74-88 — equals/hashCode, which stay as they are
- CLAUDE.md "Java 8" and coverage rules

## Acceptance
- [ ] `public enum CaseMapping { ASCII, RFC1459, STRICT_RFC1459 }` exists with public `String fold(String)` and `boolean equals(String, String)` (null-safe: two nulls equal, one null not), each with javadoc
- [ ] `fold` changes only ASCII: all three map `A-Z`→`a-z`; RFC1459 additionally maps `[ ] \ ~` → `{ } | ^`; STRICT_RFC1459 maps `[ ] \` → `{ } |` but leaves `~` alone; non-ASCII characters (e.g. `İ`, `Ä`) are returned unchanged by all three
- [ ] A public static `CaseMapping forToken(String value)` (javadoc'd) returns RFC1459 for null, maps `ascii`/`rfc1459`/`strict-rfc1459` case-insensitively, and returns ASCII for anything else (e.g. `rfc8265`)
- [ ] `ServerSupport.getCaseMapping()` returns RFC1459 before any ISUPPORT line and when the token is absent, the named mapping when present, ASCII for `CASEMAPPING=rfc8265`
- [ ] With `CASEMAPPING=rfc1459`, `tracker.getChannel("#foo[]")` finds a channel joined as `#FOO{}`, and a JOIN/PART/KICK by `Nick[1]` is matched to user `nick{1}` in ChannelState; with `CASEMAPPING=ascii` those do not match
- [ ] No `toLowerCase` or `equalsIgnoreCase` remains in ChannelStateTracker.java or ChannelState.java (`rg` shows none)
- [ ] ChannelUser equals/hashCode bodies are unchanged; its class javadoc states they compare nicks ASCII-case-insensitively regardless of the server's CASEMAPPING
- [ ] No existing public/protected signature changed; `mvn verify` passes (coverage gate and javadoc)

## Out of scope
- IRCBot, BasicIRCClient and anything in `bot/` or `impl/` (tasks 04, 06)
- Re-keying existing channel/user maps when CASEMAPPING arrives late
- Changing ChannelUser equality

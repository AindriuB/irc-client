# 03 — Add a package-private CTCP codec in the bot package

**Repo:** .
**Depends on:** none
**Owns:**
- src/main/java/io/github/aindriub/irc/client/bot/Ctcp.java
- src/test/java/io/github/aindriub/irc/client/bot/CtcpTest.java

## Goal
Parsing and building `\u0001`-delimited CTCP payloads is needed by MessageContext,
IRCBot.action and the CTCP responder (task 05). Put that logic in one small
package-private class so it can be built and tested in isolation first. It adds
no public API.

## Context
- src/main/java/io/github/aindriub/irc/client/bot/MessageContext.java:8-60 — the consumer; do not edit it here
- CTCP framing: body is `\u0001COMMAND[ SP argument]\u0001`; the trailing `\u0001` is often missing from real clients and must be tolerated

## Acceptance
- [ ] `final class Ctcp` (package-private, not `public`) with static methods to: test whether text is CTCP, return its command (upper-cased with `Locale.ROOT`), return its argument, and build a payload from command + optional argument
- [ ] `"\u0001ACTION waves\u0001"` → CTCP, command `ACTION`, argument `waves`; `"\u0001version\u0001"` → command `VERSION`, argument null; `"\u0001PING 123"` (no closing delimiter) → command `PING`, argument `123`
- [ ] `"\u0001\u0001"`, `"\u0001"`, `"hello"`, `""` and null are not CTCP; command and argument are null for them
- [ ] Building `("ACTION", "waves")` yields `"\u0001ACTION waves\u0001"`; building `("VERSION", null)` yields `"\u0001VERSION\u0001"`
- [ ] `CtcpTest` covers every branch; `mvn verify` passes

## Out of scope
- CTCP low-level quoting (`\u0010` escapes) — not implemented by modern clients
- MessageContext, IRCBot, IRCBotBuilder changes (task 05)
- DCC

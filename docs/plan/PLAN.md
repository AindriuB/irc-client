# Plan

What is open, in priority order. Only `scribe` edits this file.

Each item: one line of what, one line of why it matters, and its blocker if it
has one. When an item is in flight, mark it with its task ids. Delete items you
no longer want rather than letting them rot — a plan nobody trusts is worse
than no plan.

---

## Now

### Accumulate 1.2.0 — batch complete, ready to cut
Releases are batched rather than cut per change. A release cannot be withdrawn
or replaced, every one spends part of a monthly publishing allowance, and each
one obliges anyone downstream to decide whether to take it. `master` sits at
`1.2.0-SNAPSHOT`; the batch below is everything waiting to go out, and nothing
is blocking it — it can be cut whenever the maintainer decides to. Steps in
`RELEASING.md`.

On `master`:

- **#39** — quote what the server said when TLS never started
- **#40** — stop the reconnect loop from holding a throttle open, and
  `ServerRefusedException` with it (new public API)
- **Bot-facade pass**, tasks 01–07: `CaseMapping` and
  `ServerSupport.getCaseMapping()` (01); `IRCFormatting.strip()` (02); the
  package-private `Ctcp` codec (03); `BasicIRCClient` dropping a channel on
  self-KICK plus its own nick/CASEMAPPING tracking (04); the CTCP/formatting
  facade on `IRCBot` — `MessageContext` CTCP accessors, `action()`, and the
  opt-in VERSION/PING/TIME responder (05); `BotListener.onKick` plus opt-in
  auto-rejoin-after-kick and CASEMAPPING-aware self checks (06); and
  connection-state events — `BotListener.onDisconnected` /
  `onReconnecting` / `onGaveUp`, plus rate-limited WARN logging of refused
  writes in `AbstractClient.send` (07).

None of this reaches `irc-web` until 1.2.0 ships; it pins 1.1.0 and should
stay there.

**Release notes draft** (for the maintainer to paste into the release):

- `CaseMapping` support: channel/nick folding now follows the server's
  advertised `CASEMAPPING`, via `ServerSupport.getCaseMapping()`.
- `IRCFormatting.strip()` removes mIRC formatting/colour codes from text.
- CTCP support: `MessageContext` exposes CTCP accessors and `action()`, and
  `IRCBot` has an opt-in VERSION/PING/TIME responder.
- A bot that is kicked drops the channel from its own state; `BotListener`
  gained `onKick`, and an opt-in auto-rejoin-after-kick.
- `BotListener` gained connection-state events — `onDisconnected`,
  `onReconnecting`, `onGaveUp` — so a bot can tell it is reconnecting instead
  of silently retrying forever. `ClientConfigurationBuilder.connectionListener`
  configures the handler.
- Behaviour change: the `onReconnected` hook and `IRCBot`'s `onReady` now run
  on a dedicated connection-event thread rather than a channel loop. Slow
  work there (e.g. several flood-controlled sends) delays later connection
  events for that client.
- A write the outbound rate limiter refuses is now logged at WARN
  (rate-limited, no payload) instead of silently dropped.
- #39: a TLS handshake failure now quotes what the server said.
- #40: the reconnect loop no longer holds a throttle open;
  `ServerRefusedException` is new public API.

## Next

### Drop a JOIN-refused channel from BasicIRCClient's joined set
Found in review of 06. A channel whose JOIN the server refuses (471 full, 473
invite-only, 474 banned, 475 bad key) stays in `BasicIRCClient`'s joined set,
so every reconnect retries it once. Candidate fix: drop the channel on those
numerics.

### Replace IRCBotTest's older fixed sleeps with condition waits
`src/test/java/io/github/aindriub/irc/client/bot/IRCBotTest.java` has 16
older fixed `Thread.sleep(150-300)` waits, predating task 06, against
`conventions.md`'s "no sleeps as synchronisation. Wait on a condition or a
future." Task 06 already replaced its own tests' sleeps with marker or
condition waits; do the same for the rest of the file.

## Someday

### DCC
Not implemented, and a real decision rather than an oversight — it means
listening sockets and a second transfer protocol beside IRC.

# Plan

What is open, in priority order. Only `scribe` edits this file.

Each item: one line of what, one line of why it matters, and its blocker if it
has one. When an item is in flight, mark it with its task ids. Delete items you
no longer want rather than letting them rot — a plan nobody trusts is worse
than no plan.

---

## Now

### 1.2.1 security patch — complete, ready to cut
`master` sits at `1.2.1-SNAPSHOT`. Both tasks landed. Task 01: IRCText's
validators (and AbstractClient.send's failure messages) no longer put the
rejected value into an exception message — in production a Twitch OAuth
password saved as the whole `PASS oauth:…` line was logged at ERROR once
a minute for hours, because the whitespace check put the full token into
the message. Task 02: `ClientConfigurationBuilder.password`/`sasl` and
`IRCBotBuilder.password` now validate at configuration time, matching the
rules `PASS`/`AUTHENTICATE` already enforced, so a bad credential throws
once instead of failing silently on every reconnect; a log-capture test
(`RegistrationSecretLogTest`) proves a registration-time credential
failure never writes the secret to any log at TRACE; and
`Join.toString()` no longer exposes channel keys (found in review of 01).
This shipped as a patch rather than waiting for the next batch release
because it is a credential leak, not a feature. Nothing is blocking the
cut. Steps in `RELEASING.md`. When it is cut, also move the README
dependency snippet from `1.2.0` to `1.2.1`.

The release dry run (GitHub Actions run 36043544364) failed on a
`ReconnectTest` flake unrelated to the security fix itself; that is now
fixed on master (see HISTORY, task 99) and the dry run is unblocked.

**Release constraint:** irc-client has one Central release left in its
September 2026 publishing allowance — the dropped 1.2.0 upload already
counted against it. 1.2.1 must be right first time; there is no retry
this month if the upload needs withdrawing or redoing.

**Release notes draft** (for the maintainer to paste into the 1.2.1
release):

- Security fix: exceptions from `IRCText` validation and from
  `AbstractClient.send` failures no longer include the rejected value
  (password, SASL credential, or other caller-supplied text) — only its
  length, index of the problem, or the offending code point. If you log
  exception messages, secrets that used to appear there no longer will.
- `ClientConfigurationBuilder.password`/`sasl` and `IRCBotBuilder.password`
  now validate at configuration time, matching the rules `PASS`/
  `AUTHENTICATE` already enforced, so a bad credential throws once instead
  of failing silently on every reconnect.
- `Join.toString()` no longer exposes channel keys.
- No public API change versus 1.2.0.

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
- CTCP support: `MessageContext` exposes `isCtcp`/`getCtcpCommand`/
  `getCtcpArgument` and `getPlainText`; `IRCBot.action()` sends /me;
  `IRCBotBuilder.respondToCtcp(true)` enables a VERSION/PING/TIME responder
  (off by default).
- A bot that is kicked drops the channel from its own state; `BotListener`
  gained `onKick`, and `IRCBotBuilder.autoRejoinAfterKick(true)` enables
  auto-rejoin (off by default).
- `BotListener` gained connection-state events — `onDisconnected`,
  `onReconnecting`, `onGaveUp` — so a bot can tell it is reconnecting instead
  of silently retrying forever. `ClientConfigurationBuilder.connectionListener`
  configures the handler.
- Behaviour change: after a reconnect, the protected `onReconnected` hook and
  `IRCBot`'s `onReady` run on a dedicated connection-event thread rather than
  a channel event loop. On the first connect nothing changes. Slow work there
  (e.g. several flood-controlled sends) delays later connection events for
  that client.
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

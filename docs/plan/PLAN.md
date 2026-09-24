# Plan

What is open, in priority order. Only `scribe` edits this file.

Each item: one line of what, one line of why it matters, and its blocker if it
has one. When an item is in flight, mark it with its task ids. Delete items you
no longer want rather than letting them rot — a plan nobody trusts is worse
than no plan.

---

## Now

### Accumulate 1.2.0
Releases are batched rather than cut per change. A release cannot be withdrawn
or replaced, every one spends part of a monthly publishing allowance, and each
one obliges anyone downstream to decide whether to take it. `master` sits at
`1.2.0-SNAPSHOT` until there is a batch worth shipping.

Waiting to go out:

- **#39** — quote what the server said when TLS never started
- **#40** — stop the reconnect loop from holding a throttle open, and
  `ServerRefusedException` with it

Both are on `master` and neither reaches `irc-web` until this ships; it pins
1.1.0 and should stay there. New public API in #40, so 1.2.0 rather than a
patch.

Cut it when the list stops growing, or sooner if something on it is bad enough
that somebody is waiting. Steps in `RELEASING.md`.

### Bot-facade pass for 1.2.0
The split between "the low level refuses, the facade helps" was deliberate but
only applied to message length. Decided what else belongs there: a bot that
gets kicked should not sit in a channel it thinks it is in, CTCP and formatted
text should not leak into MessageContext as raw control bytes, and a bot
should be able to tell it is reconnecting rather than silently retrying
forever. Landed so far: `CaseMapping` and `ServerSupport.getCaseMapping()`
(task 01), `IRCFormatting.strip()` (task 02), the package-private `Ctcp`
codec (task 03). In flight: BasicIRCClient drops a channel on KICK (04),
CTCP/plain text on the bot facade (05), `onKick`/auto-rejoin/CASEMAPPING-aware
self checks on the bot (06), and connection-state events — DISCONNECTED /
RECONNECTING / RECONNECTED / GAVE_UP — on `BotListener` (07).

## Someday

### DCC
Not implemented, and a real decision rather than an oversight — it means
listening sockets and a second transfer protocol beside IRC.

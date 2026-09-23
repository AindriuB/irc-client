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

- **#39** — quote what the server said when TLS never started *(merged)*
- **#40** — stop the reconnect loop from holding a throttle open *(open)*

Neither reaches `irc-web` until this ships; it pins 1.1.0 and should stay there.

Cut it when the list stops growing, or sooner if something on it is bad enough
that somebody is waiting. Steps in `RELEASING.md`.

## Next

### Decide what else belongs in the bot facade
The split between "the low level refuses, the facade helps" is deliberate but
only applied to message length so far. Worth a pass over the facade asking what
else a bot author is currently expected to get right alone.

## Someday

### DCC
Not implemented, and a real decision rather than an oversight — it means
listening sockets and a second transfer protocol beside IRC.

# Plan

What is open, in priority order. Only `scribe` edits this file.

Each item: one line of what, one line of why it matters, and its blocker if it
has one. When an item is in flight, mark it with its task ids. Delete items you
no longer want rather than letting them rot — a plan nobody trusts is worse
than no plan.

---

## Now

### Release 1.1.0
`master` is `1.1.0-SNAPSHOT` and carries two merged changes with new public
API — `outboundQueueDepth` and `OutboundQueueFullException` from #36,
`PrivMsg.split`/`Notice.split` and `IRCText.requireFits` from #37. Until it is
released, `irc-web` cannot use either: it pins 1.0.0. Steps in `RELEASING.md`.

## Next

### Decide what else belongs in the bot facade
The split between "the low level refuses, the facade helps" is deliberate but
only applied to message length so far. Worth a pass over the facade asking what
else a bot author is currently expected to get right alone.

## Someday

### DCC
Not implemented, and a real decision rather than an oversight — it means
listening sockets and a second transfer protocol beside IRC.

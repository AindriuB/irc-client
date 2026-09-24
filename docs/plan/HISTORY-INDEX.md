# History index

One row per `## YYYY-MM-DD` entry in `HISTORY.md`, newest first. Only `scribe`
edits this file, and it writes the row in the same commit as the entry.

`HISTORY.md` grows without bound and is the single largest file a session can
accidentally load. This index exists so `planner` can answer "has this been
built before, and where do I read about it?" without opening it. Rows are taken
from `HISTORY.md`'s own headings rather than re-summarized, so the index can be
checked against its source by eye.

Read a full entry by grepping the exact string in the **Heading** column:

```
grep -n '<heading text>' docs/plan/HISTORY.md
```

Then read that section only. A stale index is worse than none — an entry with
no row is one `planner` cannot find, and will re-plan.

| Date | Task IDs | Summary | Heading (grep this exact string) |
|---|---|---|---|
| 2026-09-24 | 99 | Fixed ReconnectTest's no-nick-set flake (StubIRCServer accept-thread race) that had failed the 1.2.1 release dry run; test-only, unblocks the cut | ## 2026-09-24 — Fixed the ReconnectTest flake that blocked the 1.2.1 dry run |
| 2026-09-24 | 02 | 1.2.1 security patch complete, ready to cut: credential validation moved to configuration time (builder setters), a log-capture test proves a registration-time failure never logs the secret, Join.toString redacts channel keys | ## 2026-09-24 — Credentials validate at configuration time, closing the 1.2.1 security patch |
| 2026-09-24 | 01 | 1.2.1 security patch: IRCText validators and AbstractClient.send no longer put rejected values in exception messages; fixes a Twitch OAuth password logged at ERROR in production | ## 2026-09-24 — IRCText and AbstractClient.send stop echoing rejected values |
| 2026-09-24 | 07 | Connection-state events (DISCONNECTED/RECONNECTING/RECONNECTED/GAVE_UP) on BotListener merged to master, closing the bot-facade pass; 1.2.0 ready to cut | ## 2026-09-24 — Connection-state events land, closing the 1.2.0 bot-facade pass |
| 2026-09-24 | 06 | BotListener.onKick and opt-in auto-rejoin-after-kick merged to master (bot-facade pass wave 3) | ## 2026-09-24 — IRCBot reports kicks and can auto-rejoin |
| 2026-09-24 | 04, 05 | BasicIRCClient drops kicked channels; bot CTCP responder merged to master (bot-facade pass wave 2) | ## 2026-09-24 — BasicIRCClient drops kicked channels, and the bot CTCP responder lands |
| 2026-09-24 | 01, 02, 03 | CaseMapping, IRCFormatting.strip, Ctcp codec merged to master (bot-facade pass wave 1) | ## 2026-09-24 — CaseMapping, IRCFormatting.strip and the Ctcp codec land |

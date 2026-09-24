# History

What was built, newest first. Append-only. Only `scribe` edits this file.

**Read this before redesigning anything.** The `Cost` line on each entry is the
point of the file — it is where the sessions that already tried the obvious
approach tell you what happened.

Do not load this file to find out whether something exists. It grows without
bound. `HISTORY-INDEX.md` carries one row per entry; scan that, then grep this
file for the exact heading it names. Every entry added here gets its index row
in the same commit.

<!--
## YYYY-MM-DD — <what landed>
<Two or three sentences: what it does now that it did not before.>
**Cost:** <what was hard, what was tried and abandoned, what not to retry.>
-->

## 2026-09-24 — CaseMapping, IRCFormatting.strip and the Ctcp codec land
Wave 1 of the 1.2.0 bot-facade pass. `CaseMapping` (ASCII, RFC1459,
STRICT_RFC1459) and `ServerSupport.getCaseMapping()` read the server's
CASEMAPPING and drive channel/nick folding in the state package;
`ChannelUser` equality stays plain Java `equalsIgnoreCase`, documented as
such. `IRCFormatting.strip(String)` removes mIRC formatting codes including
`\u0003` colour and `\u0004` hex colour. A package-private `Ctcp` codec
parses and builds CTCP frames for the bot facade tasks (05+) to use.
**Cost:** Ctcp took three attempts. The first two review rounds both failed
on the same thing: `build()` validated nothing, so a stranger's PING
argument echoed straight back through `build()` could embed a second CTCP
frame or throw. The fix that stuck was an explicit invariant, not a pile of
individual checks: for every `t`, if `isCtcp(t)` is true then
`build(command(t), argument(t))` must never throw — `isCtcp`/`argument` were
tightened (empty command, NUL/CR/LF, missing closing delimiter) so parse
output is always safe to hand back to `build()`, while `build()` itself stays
strict for direct callers. `IRCFormatting` needed a similar correction on
attempt 2: `\u0004` hex colour takes exactly six hex digits, not up to six,
and a background colour only counts after a foreground digit was already
seen — both apply to `\u0003` too.

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

## 2026-09-24 — IRCText and AbstractClient.send stop echoing rejected values

Task 01 of the 1.2.1 security patch, merged to master. IRCText's
validators (used by `Pass`, `Authenticate`, and everything else that
validates an IRC parameter) no longer put the rejected value into an
`IllegalArgumentException` message; they now give the parameter name,
its index and length, or a `U+XXXX` code point for CR/LF/NUL.
`AbstractClient.send`'s failure messages report the payload's length
instead of the payload itself. No public or protected signature changed
versus 1.2.0. Found in production: a user's Twitch OAuth password was
saved as the whole line `PASS oauth:…`; the whitespace check put the
full token into an exception message, and irc-web logged it at ERROR
once a minute for hours (irc-web was orphaning a failed bot and
retrying it in a loop, fixed separately in irc-web). The library's
debug logs already masked passwords, so this was a leak specific to the
validation-exception path.

**Cost:** the same sweep found `Join.toString()` exposes channel keys
(the library's own logging only calls `getChannels`, but any caller
that logs a keyed `Join` logs the key). Folded into task 02 rather than
fixed here, since it touches a different file and test than task 01 owns.

## 2026-09-24 — Connection-state events land, closing the 1.2.0 bot-facade pass
Wave 4, the last of the pass. `ConnectionEvent` (DISCONNECTED / RECONNECTING /
RECONNECTED / GAVE_UP, with attempt/delay accessors) reaches bot code through
`BotListener.onDisconnected` / `onReconnecting` / `onGaveUp` and through
`ClientConfigurationBuilder.connectionListener` / `ClientConfiguration`'s
connection-handler accessors, so a bot can tell it is reconnecting instead of
silently retrying forever. All connection events for a client are delivered
one at a time, strictly in order, on a single lazily started daemon thread
("irc-connection-events") — a deliberate `disconnect()`/`stop()` publishes
nothing. `disconnect()` waits up to 5s for that thread to drain, skipping the
wait when called from a handler so a handler cannot deadlock itself. Also
landed while in `AbstractClient.send`: a write the outbound rate limiter
refuses now logs at WARN, rate-limited to about one line per interval plus a
count, with no payload. Behaviour change worth flagging in release notes: the
protected `onReconnected` hook and `IRCBot`'s `onReady` now run on the
connection-event thread rather than a channel loop, so slow work there (e.g.
several flood-controlled sends) delays later connection events; this is
documented on the affected javadoc. This closes the bot-facade pass; 1.2.0 is
ready to cut alongside #39/#40.
**Cost:** Attempt 1 published RECONNECTING after scheduling the reconnect on
a Netty event-loop thread, so under `reconnectBackoff(0, …)` against a closed
port, RECONNECTING for attempt 2 could arrive before attempt 1, and a slow
handler could see events from two threads concurrently — the ordering test
failed 3/3 against it. Fixed by moving all publication onto one serial daemon
thread rather than trying to order across loops. A dedicated `ExecutorService`
for that thread also meant `disconnect()` calling `executor.awaitTermination`
would deadlock if invoked from a handler running on that same executor;
`disconnect()` now detects that case and skips the wait. The thread was
originally non-daemon and had to be fixed to daemon so it does not keep the
JVM alive after the client is done with it.

## 2026-09-24 — IRCBot reports kicks and can auto-rejoin
Wave 3 of the 1.2.0 bot-facade pass. `BotListener` gains
`onKick(bot, channel, kicked, by, reason)`, a default no-op dispatched
through `safely` for every KICK, null-safe for a server-issued `by`.
`IRCBotBuilder.autoRejoinAfterKick(boolean)` (default off) sends one JOIN
per self-KICK and never retries on 474; the rejoin lands after
`BasicIRCClient`'s own kick handler has already dropped the channel from
its joined set, so the rejoined channel stays tracked. It does not reuse a
channel key. `isFromSelf` and the self-KICK check now go through
`client.getCaseMapping()` instead of a plain string comparison. Only
connection-state events (task 07) remain before 1.2.0 can be cut.
**Cost:** first pass of the tests used fixed sleeps as synchronisation,
against `conventions.md`; review sent it back and the fix added a marker/
condition-wait test for KICK → auto-rejoin → reconnect instead. The file's
16 older fixed sleeps predate this task and are unchanged; that cleanup is
now a separate PLAN item rather than folded into 06's scope.

## 2026-09-24 — BasicIRCClient drops kicked channels, and the bot CTCP responder lands
Wave 2 of the 1.2.0 bot-facade pass. `BasicIRCClient` now tracks its own nick
(via RPL_WELCOME and NICK) and the server's `CASEMAPPING` (via ISUPPORT,
including `-CASEMAPPING` negation) through an always-present pipeline
handler, exposed as `getCaseMapping()`. An inbound KICK of self drops the
channel from the joined set, so a reconnect no longer rejoins it; outbound
KICK never touches that set. Joined channels fold under the current mapping,
keeping the latest spelling and key. `MessageContext` gains `isCtcp`,
`getCtcpCommand`, `getCtcpArgument` and `getPlainText`; `IRCBot` gains
`action()`; `IRCBotBuilder.respondToCtcp(boolean)` (default off) turns on an
automatic VERSION/PING/TIME responder that replies by NOTICE only, skips
server-prefixed CTCP probes (null sender), and drops a reply that would not
fit on one line rather than splitting it.
**Cost:** Both branches needed a second attempt. 04: per-connection state
(self nick, CASEMAPPING token) survived a reconnect and was folded against
before the new ISUPPORT arrived, and JOIN keys were not folded so
`JOIN #A` then `JOIN #a` produced two entries instead of one — both are now
reset/folded per connection. 05: a server's CTCP VERSION probe on connect
has a server prefix, so `sender` is null; the first attempt let that null
propagate into `notice()`, which threw and skipped `dispatchCommand` and
every `onMessage` listener for the whole message. The responder now returns
early on a null sender and wraps its own reply in a try/catch that logs at
WARN instead of escaping into the pipeline.

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

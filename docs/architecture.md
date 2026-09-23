# Architecture

The shape of the library: what the pieces are, what talks to what, and where the
boundaries are. `explorer` and `architect` read this before inferring structure
from the filesystem, so keeping it honest saves every session a search.

## Components

| Package | Owns |
|---|---|
| `message/` | `IRCMessage`, the parser, numerics. The wire format and nothing else |
| `command/` | One type per outbound command. Each renders itself |
| `handler/` | The Netty pipeline: framing, registration, ping, rate limiting |
| `impl/` | `AbstractClient` and `BasicIRCClient` — connection lifecycle |
| `configuration/` | Immutable config and its builder. The only way in |
| `state/` | Channel and user tracking, `ServerSupport` from ISUPPORT |
| `event/` | The listener contract |
| `bot/` | `IRCBot`, the facade most users actually hold |

Two layers, deliberately. The low level refuses what the server would mangle;
the facade is allowed to be helpful. `bot.say(..)` splits a message too long for
one line, while building a `PrivMsg` by hand throws — a bot's text is usually
assembled from something it did not choose, and failing there moves the problem
to every bot author.

## The pipeline

Order is the design. Inbound runs head → tail, outbound tail → head, so an
outbound handler placed after the encoder never sees the message.

```
ssl? → logging → lineBasedFrameDecoder → stringDecoder → lineEncoder
     → idleStateHandler → idleConnectionHandler → pingHandler
     → registrationHandler → inboundMessageEventHandler → outboundRateLimiter
```

- `LineBasedFrameDecoder(MAX_FRAME_SIZE)` is **inbound only**. Outbound length
  is checked by `IRCText.requireFits`, not here.
- `LineEncoder` with `LineSeparator.WINDOWS`: IRC is CRLF, and a bare LF is
  accepted by some servers and silently dropped by others.
- `OutboundRateLimiter` is last so it sees everything, including what the
  registration handler sends.

## Boundaries that must not be crossed

1. **Nothing reaches the wire without passing `IRCText`.** A CR, LF or NUL in a
   parameter injects a second IRC message — `new Join("#chan\r\nQUIT")` renders
   as two. Every command validates its own parameters; a new command that skips
   this is a defect regardless of how it reads.
2. **`render()` is the wire, `toString()` is the log.** `Pass` redacts in
   `toString`. A command sent via its `toString` would put the literal text
   `<redacted>` on the wire; that bug has already happened once.
3. **Byte length, not character length.** The 512-byte limit is counted in
   bytes, and in UTF-8 an emoji is one character and four. Splitting counts by
   code point so a split never lands between surrogate halves.
4. **`getTrailing()` is not "the last parameter".** Use `hasTrailing()` to ask
   whether there was one. Conflating them broke `CAP * LS`, `353` and `332`.
5. **Java 8 only.** No `var`, no `List.of`, no `String.strip*`, no
   `Stream.toList`, no `Optional.or`.

## Decisions worth knowing

- **Netty 4.1.x, slf4j 1.7.x, logback 1.2.x** — the last lines that stay Java 8
  compatible. Pinned in `.github/dependabot.yml`, which ignores only those
  ranges: patch and security updates inside them are still wanted, and that is
  how #7 cleared eight advisories. Each ignore records the PR that declined the
  major bump, so the decision can be argued with.
- **Specific Netty modules, not `netty-all`.** A library should not drag HTTP/2,
  DNS and QUIC onto its consumers.
- **JUnit 4.** Inherited from the original codebase rather than chosen; no
  recorded reason, and nothing here depends on JUnit 4 specifically. Worth
  revisiting only alongside dropping Java 8.
- **`--release 8` behind a `[9,)` profile** rather than `source`/`target` alone,
  which compile against whatever JDK is running and let a newer API slip
  through to a runtime failure.
- **Coverage gate at 97%/95%** — a floor set just below where the suite sits, so
  coverage cannot rot unnoticed without failing on a rounding difference.
- **Outbound queue bounded at 30 by default** (2026-09-22). Netty's write
  watermarks cannot help, because the limiter accepts every write immediately
  and completes nothing until a token arrives. `0` restores the old unbounded
  behaviour.

## Repository topology

Self-contained: `git clone` and `mvn verify`. No sibling checkout is required
and none is read. `irc-web` consumes this library from Maven Central as an
ordinary dependency — it is not a module here and this repository knows nothing
about it.

Releases go to Maven Central through the Central Portal with `autoPublish=false`;
the staged deployment is confirmed by hand. See `RELEASING.md`. The signing key
and portal tokens live in the KeePass vault on the homelab, never in this repo.

`.claude/` and `.worktrees/` are gitignored on purpose. `.claude/` holds the
worktree scripts installed from the agent kit, whose single clone lives at
`~/.claude-kit` and is refreshed with `git -C ~/.claude-kit pull`. The roles are
symlinked into `~/.claude` and shared by every project; tracking any of it here
would fork the harness away from that upstream.

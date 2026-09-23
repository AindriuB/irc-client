# irc-client

<!-- HARD CAP: 50 lines. This file is a router, not a manual.
     If you are adding detail here, it belongs in docs/ instead. -->

A Java IRC client library on Netty, published to Maven Central as
`io.github.aindriub:irc-client`. **Java 8 is the target** and that constraint
shapes every change. `mvn verify` builds and tests it at this level.

## Read on demand, not up front

| Need | File |
|---|---|
| How we work (the loop, roles, parallelism) | `docs/workflow.md` |
| Code style, naming, commit format | `docs/conventions.md` |
| What is open, in priority order | `docs/plan/PLAN.md` |
| What was already built — scan, never open `HISTORY.md` whole | `docs/plan/HISTORY-INDEX.md` |
| One task's full contract | `docs/plan/tasks/<id>.md` |
| Pipeline order, threading, the public surface | `docs/architecture.md` |
| Cutting a release | `RELEASING.md` |

Load exactly one of these when the task needs it. Do not preload the set.

## Before changing anything

- **Java 8.** `maven.compiler.release=8` is enforced under a `[9,)` profile, so
  a Java 9+ API fails at compile time rather than on a user's runtime. It has
  caught `List.of`, `String.stripTrailing` and `Stream.toList` already.
- **Coverage is a gate, not a report.** `mvn verify` fails below 97% instruction
  and 95% branch. Adding a branch means adding the test that covers it.
- **`mvn verify`, not `mvn test`.** Integration tests are `*IT` and need
  `-Pintegration-test` plus a real IRC server.
- This is a published library. A changed method signature or a new required
  constructor argument is a breaking change for people who are not here.

## Rules that hold everywhere

1. One task = one worktree = one branch. Never two agents in one tree.
2. A task file names the files it owns. Editing outside that set is a bug —
   stop and report instead.
3. Never paste file contents into a summary. Cite `path:line`.
4. Prefer `rg` over `grep`, and read ranges over whole files.
5. Secrets, tokens and dumps never leave the machine and never enter a doc.
6. Only `scribe` writes docs. Only `implementer` writes code.

## Roles

`explorer` recon · `architect` shape · `planner` tasks · `implementer` code ·
`tester` builds · `reviewer` diffs · `scribe` docs. Definitions in
`~/.claude/agents/`. The loop is `/plan` → `/fanout` → `/verify` → `/record`.

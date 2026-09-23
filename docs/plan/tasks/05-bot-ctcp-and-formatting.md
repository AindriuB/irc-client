# 05 — Surface CTCP and plain text in the bot facade

**Repo:** .
**Depends on:** 02, 03
**Owns:**
- src/main/java/io/github/aindriub/irc/client/bot/IRCBot.java
- src/main/java/io/github/aindriub/irc/client/bot/IRCBotBuilder.java
- src/main/java/io/github/aindriub/irc/client/bot/MessageContext.java
- src/test/java/io/github/aindriub/irc/client/bot/IRCBotTest.java

## Goal
Bot authors currently parse CTCP and strip mIRC formatting by hand, and a bold
`!hello` silently fails to dispatch. Add CTCP accessors and plain text to
MessageContext, an `action` sender, an opt-in CTCP responder, and dispatch
commands on formatting-stripped text. Existing bots must see no behaviour change
unless they opt in.

## Context
- src/main/java/io/github/aindriub/irc/client/bot/MessageContext.java:16-60 — accessors to extend
- src/main/java/io/github/aindriub/irc/client/bot/IRCBot.java:159-173 — `say`/`notice` split long text; `action` follows the same policy
- src/main/java/io/github/aindriub/irc/client/bot/IRCBot.java:269-307 — Router onMessage/onNotice
- src/main/java/io/github/aindriub/irc/client/bot/IRCBot.java:389-408 — `dispatchCommand`
- src/main/java/io/github/aindriub/irc/client/bot/IRCBot.java:68-94 — constructor; src/main/java/io/github/aindriub/irc/client/bot/IRCBotBuilder.java:104-133 — builder option style and `build()`
- src/main/java/io/github/aindriub/irc/client/bot/Ctcp.java (task 03), src/main/java/io/github/aindriub/irc/client/message/IRCFormatting.java (task 02)

## Acceptance
- [ ] `MessageContext.isCtcp()`, `getCtcpCommand()`, `getCtcpArgument()` (javadoc'd) delegate to Ctcp: for `\u0001ACTION waves\u0001` they return true/`ACTION`/`waves`; for plain text false/null/null
- [ ] `MessageContext.getPlainText()` (javadoc'd) returns `IRCFormatting.strip(getText())`; `getText()` is unchanged
- [ ] `IRCBot.action(target, text)` (javadoc'd) sends `PRIVMSG target :\u0001ACTION text\u0001`; text too long for one line is split into several lines, each a complete `\u0001ACTION ...\u0001` within the line limit
- [ ] `IRCBotBuilder.respondToCtcp(boolean)` (javadoc'd, default false). When true, a PRIVMSG CTCP from another user gets a NOTICE to the sender: `VERSION` → `\u0001VERSION <non-empty>\u0001`, `PING 123` → `\u0001PING 123\u0001`, `TIME` → `\u0001TIME <non-empty>\u0001`
- [ ] No reply is sent: when the option is false (default); for CTCP received as NOTICE; for CTCP from self; for ACTION or unknown commands
- [ ] CTCP PRIVMSGs still reach `BotListener.onMessage` with `getText()` unchanged, with the responder on or off
- [ ] A PRIVMSG of `\u0002!hello\u0002 world` dispatches the `!hello` handler with args `[world]`; an ACTION `\u0001ACTION !hello\u0001` does not dispatch
- [ ] No existing public/protected signature changed; `mvn verify` passes (coverage and javadoc)

## Out of scope
- KICK, auto-rejoin, BotListener, isFromSelf/case mapping (task 06)
- DCC or any CTCP beyond VERSION/PING/TIME/ACTION
- Making the VERSION reply string configurable

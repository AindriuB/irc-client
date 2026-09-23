# 02 — Add IRCFormatting.strip for mIRC control codes

**Repo:** .
**Depends on:** none
**Owns:**
- src/main/java/io/github/aindriub/irc/client/message/IRCFormatting.java
- src/test/java/io/github/aindriub/irc/client/message/IRCFormattingTest.java

## Goal
Bots receive text decorated with mIRC formatting codes and have no way to see the
plain text. Add a public utility that removes those codes, so the facade (task 05)
can expose plain text and match commands on it.

## Context
- src/main/java/io/github/aindriub/irc/client/IRCText.java:1-30 — shape of an existing final static-utility class (private ctor, class javadoc)
- src/main/java/io/github/aindriub/irc/client/message/package-info.java — package the new class joins

## Acceptance
- [ ] `public final class IRCFormatting` with a private constructor and javadoc'd `public static String strip(String text)`; `strip(null)` returns null
- [ ] Removes `\u0002` bold, `\u001D` italic, `\u001F` underline, `\u001E` strikethrough, `\u0011` monospace, `\u0016` reverse, `\u000F` reset
- [ ] `\u0003` colour is removed together with up to two fg digits and, only if followed by a digit, a comma plus up to two bg digits: `"\u000304,12hi"`→`"hi"`, `"\u00034hi"`→`"hi"`, `"\u0003,hi"`→`",hi"`, `"\u000304,x"`→`",x"`, `"\u0003123"`→`"3"`
- [ ] `\u0004` hex colour is removed together with up to six hex digits and optionally `,` plus up to six hex digits: `"\u0004FF0000,00FF00hi"`→`"hi"`
- [ ] Text without codes is returned equal (`strip("plain !cmd")` equals `"plain !cmd"`); `\u0001` (CTCP delimiter) is not removed
- [ ] Unit tests cover every branch; `mvn verify` passes

## Out of scope
- Adding formatting (builders for bold/colour output)
- Any change to MessageContext or IRCBot (task 05)

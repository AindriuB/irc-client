# irc-client

[![build](https://github.com/AindriuB/irc-client/actions/workflows/build.yml/badge.svg)](https://github.com/AindriuB/irc-client/actions/workflows/build.yml)

Simple IRC client library built on Netty, for a chat bot I'm working on.

## Writing a bot

```java
IRCBot bot = IRCBot.builder()
        .host("irc.example.org")
        .nick("mybot")
        .password("hunter2")              // optional; Twitch takes oauth:... here
        .channels("#chat")
        .command("!hello", (context, args) -> context.reply("hello " + context.getSender()))
        .listener(new BotListener() {
            @Override
            public void onMessage(MessageContext context) {
                if (context.getText().contains("ping")) {
                    context.reply("pong");
                }
            }
        })
        .build();

bot.start();   // connects, registers, joins, and returns once it is in its channels
```

`context.reply(..)` answers where the message came from — the channel for a channel
message, the sender for a direct one. `replyDirectly(..)` always goes to the person
and `replyNotice(..)` sends a NOTICE. The bot never receives its own messages, so it
cannot answer itself into a loop, and a listener that throws is logged rather than
taking the connection down.

The bot tracks who is in each channel and what status they hold, built from what the
server sends rather than by polling:

```java
ChannelState channel = bot.getChannelState("#chat");
channel.getUsers();                       // everyone, with their statuses
channel.getOperators();                   // operator and above
channel.getUser("someone").isOperator();
channel.getTopic();
```

It follows joins, parts, quits, kicks, nick changes and mode changes, and rebuilds a
channel from scratch on each NAMES reply so a reconnect leaves no ghosts behind. Mode
parsing uses the server's own `CHANMODES` and `PREFIX` definitions, read from its
`RPL_ISUPPORT` lines, so it knows which modes consume an argument rather than
guessing: `bot.getChannelStateTracker().getServerSupport()` exposes the rest of what
the server advertised.
`trackChannelState(false)` switches it off for a bot that never asks.

`onReady` fires once the bot is in its channels, and again after every reconnect.
`builder.client()` reaches the full connection configuration — TLS, timeouts, flood
protection, reconnection — for anything the bot builder does not surface. For lower
level use, drive `BasicIRCClient` directly as below.

## Coordinates

```xml
<dependency>
    <groupId>io.github.aindriub</groupId>
    <artifactId>irc-client</artifactId>
    <version>1.2.0</version>
</dependency>
```

Everything lives under `io.github.aindriub.irc.client`.

## Usage

```java
ClientConfiguration configuration = new ClientConfigurationBuilder()
        .host("irc.example.org")
        .port(6697)
        .secure(true)
        .nick("mybot")                    // enables the registration handshake
        .password("hunter2")              // optional; Twitch takes oauth:... here
        .capability("twitch.tv/tags")     // optional IRCv3 capabilities
        .eventListener(new LoggingEventHandler())
        .build();

BasicIRCClient client = new BasicIRCClient(configuration);
client.connect();                         // returns once the server accepts registration
client.sendCommand(new Join("#channel"));
client.sendCommand(new PrivMsg("#channel", "hello"));
client.disconnect();                      // sends QUIT, then closes
```

Outbound messages are rate limited so the server does not disconnect you for
flooding: five may go back to back, then one every two seconds by default. `PONG`
and the registration handshake bypass it, since delaying those would cause the very
timeouts they prevent. A connection that goes silent for three minutes is pinged,
and closed if it still says nothing, which hands over to the reconnect logic.

```java
.floodProtection(5, 2000)   // burst, then one per interval; .floodProtection(false) to disable
.outboundQueueDepth(30)     // messages that may wait behind it; 0 removes the limit
.readTimeout(180000)        // silence before the liveness check; 0 to disable
```

The queue behind flood protection is bounded. Producing faster than the rate fails
the send with `OutboundQueueFullException` rather than growing memory quietly —
Netty's write watermarks cannot help here, because the limiter accepts every write
immediately and the channel never sees anything outstanding. Catching it is a
bot's cue to slow down or drop the message; raising the depth trades that error for
memory.

If the connection drops unexpectedly the client reconnects with exponential backoff,
re-registers and rejoins its channels. A deliberate `disconnect()` never reconnects,
and a disconnected client cannot be reused — build a new one. `send()` throws rather
than silently reconnecting, because a silent reconnect would leave an unregistered
connection that rejects every command.

For networks that want SASL rather than a NickServ message:

```java
.sasl("account", "password")   // adds the sasl capability for you
```

SASL PLAIN sends the password base64 encoded rather than hashed, so use it over TLS.
`AUTHENTICATE` redacts itself in logs, as `PASS` does, and `CAP END` is held back
until authentication finishes, since that is the only window the server leaves open
for it.

Setting a nick turns on registration: on connect the client negotiates capabilities,
sends `PASS`/`NICK`/`USER`, retries with a modified nick if yours is taken, and waits
for `RPL_WELCOME` before `connect()` returns. Leave the nick unset to drive the
handshake yourself.

## Reacting to messages

```java
.messageListener(new MessageListener() {
    @Override
    protected void onMessage(String target, String sender, String text, IRCMessage raw) {
        if (text.startsWith("!hello")) {
            client.sendCommand(new Notice(target, "hello " + sender));
        }
    }
})
```

`MessageListener` dispatches to `onMessage`, `onNotice`, `onJoin`, `onPart`, `onQuit`,
`onKick`, `onNickChange`, `onNumeric` and `onOther`; override only what you need. Each
callback also receives the raw `IRCMessage`, so tags, the full prefix and the remaining
parameters stay reachable. `IRCMessage.isChannel(target)` distinguishes a channel
message from a direct one.

Raw-line subscribers (`eventListener`) still work and can be used alongside. Parsing
only happens when a `messageListener` is registered, and `IRCMessageParser.parse(line)`
is available directly if you want to do it yourself.

## Commands

Registration: `Pass`, `Nick`, `User`, `Cap` (`ls`/`req`/`end`)
Channels: `Join` (with keys, and `Join.partAll()`), `Part` (with reason), `Names`,
`ListChannels`, `Topic`, `Mode`, `Kick`, `Invite`
Messaging: `PrivMsg`, `Notice`
Users: `Who`, `Whois`, `Whowas`, `Ison`, `Userhost`
Keepalive: `Ping`, `Pong` — server pings are answered for you
Session: `Quit` (with reason), `Away`

Commands validate their arguments and reject anything containing CR, LF or NUL, so
input taken from chat cannot inject a second IRC message. `render()` is the wire
form; `toString()` is the log form, and differs only for `Pass`, which redacts.

### Message length

IRC caps a message at 512 bytes including the CRLF, and a server **truncates** an
over-long line rather than refusing it — so the part that does not fit is lost and
the part that does looks like what you meant to say.

`bot.say(..)` and `bot.notice(..)` split, because a bot's text is usually assembled
from something it did not choose. Building the command yourself does not:
`sendCommand(new PrivMsg(..))` and `send(..)` refuse an over-long line, so nothing
is ever split behind your back.

```java
PrivMsg.split("#chan", text, StandardCharsets.UTF_8)   // pieces that fit
```

The limit is counted in **bytes, not characters** — an emoji is one character and
four bytes in UTF-8 — so the splitter takes the connection's charset and never cuts
a character in half. It prefers to break on a space, falling back to the byte budget
for a long run with none, such as a URL.

Messages are CRLF terminated by the pipeline, so commands and raw `send(..)` payloads
should not include a line ending. Server `PING` is answered automatically.

`slf4j-api` is the only logging dependency; pick your own binding.

## Licence

Apache License 2.0. See [LICENSE](LICENSE).

## Building

```
mvn test                          # unit tests, no network
mvn verify                        # also enforces the coverage floor
mvn verify -Pintegration-test     # also runs *IT tests against a real server
```

Coverage is reported by JaCoCo to `target/site/jacoco/` and floored at 97% instruction
/ 95% branch by `mvn verify`, which CI runs on Java 8, 17 and 21. Tests that need a server run against an in-process stub
(`StubIRCServer`), including over TLS, so the whole suite runs without network access.

# irc-client

Simple IRC client library built on Netty, for a chat bot I'm working on.

## Coordinates

```xml
<dependency>
    <groupId>io.github.aindriub</groupId>
    <artifactId>irc-client</artifactId>
    <version>0.0.1-SNAPSHOT</version>
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

Setting a nick turns on registration: on connect the client negotiates capabilities,
sends `PASS`/`NICK`/`USER`, retries with a modified nick if yours is taken, and waits
for `RPL_WELCOME` before `connect()` returns. Leave the nick unset to drive the
handshake yourself.

Inbound lines can be parsed with `IRCMessageParser.parse(line)`, which handles
prefixes, numeric replies and IRCv3 tags.

## Commands

Registration: `Pass`, `Nick`, `User`, `Cap` (`ls`/`req`/`end`)
Channels: `Join` (with keys, and `Join.partAll()`), `Part` (with reason), `Who`
Messaging: `PrivMsg`, `Notice`
Keepalive: `Ping`, `Pong` — server pings are answered for you
Session: `Quit` (with reason)

Commands validate their arguments and reject anything containing CR, LF or NUL, so
input taken from chat cannot inject a second IRC message. `render()` is the wire
form; `toString()` is the log form, and differs only for `Pass`, which redacts.

Messages are CRLF terminated by the pipeline, so commands and raw `send(..)` payloads
should not include a line ending. Server `PING` is answered automatically.

`slf4j-api` is the only logging dependency; pick your own binding.

## Building

```
mvn test                          # unit tests, no network
mvn verify -Pintegration-test     # also runs *IT tests against a real server
```

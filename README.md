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
        .eventListener(new LoggingEventHandler())
        .build();

BasicIRCClient client = new BasicIRCClient(configuration);
client.connect();
client.sendCommand(new Join("#channel"));
client.disconnect(); // sends QUIT, then closes
```

Messages are CRLF terminated by the pipeline, so commands and raw `send(..)` payloads
should not include a line ending. Server `PING` is answered automatically.

`slf4j-api` is the only logging dependency; pick your own binding.

## Building

```
mvn test                          # unit tests, no network
mvn verify -Pintegration-test     # also runs *IT tests against a real server
```

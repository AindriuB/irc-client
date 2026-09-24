# 07 — Report connection loss, reconnect attempts and give-up to listeners

**Repo:** .
**Depends on:** 06
**Owns:**
- src/main/java/io/github/aindriub/irc/client/event/ConnectionEvent.java
- src/main/java/io/github/aindriub/irc/client/impl/AbstractClient.java
- src/main/java/io/github/aindriub/irc/client/configuration/ClientConfiguration.java
- src/main/java/io/github/aindriub/irc/client/configuration/ClientConfigurationBuilder.java
- src/main/java/io/github/aindriub/irc/client/bot/BotListener.java
- src/main/java/io/github/aindriub/irc/client/bot/IRCBot.java
- src/test/java/io/github/aindriub/irc/client/event/ConnectionEventTest.java
- src/test/java/io/github/aindriub/irc/client/impl/ConnectionEventsTest.java
- src/test/java/io/github/aindriub/irc/client/configuration/ClientConfigurationBuilderTest.java
- src/test/java/io/github/aindriub/irc/client/bot/IRCBotTest.java

## Goal
The client reconnects on its own (unlimited attempts by default) but tells nobody,
so an application such as irc-web keeps showing "connected" while it is actually
retrying. Publish connection-state changes through the existing
`EventHandler<T>`/`Event<T>` subscriber mechanism, and surface them on the bot
facade as default no-op `BotListener` callbacks. Purely additive. Depends on 06
because 05/06 own IRCBot, BotListener and IRCBotTest (and 04, reached through 06,
owns BasicIRCClient and ReconnectTest).

## Context
- src/main/java/io/github/aindriub/irc/client/event/Event.java, EventHandler.java — the subscriber mechanism to reuse; do not add a second listener style
- src/main/java/io/github/aindriub/irc/client/handler/AbstractInboundEventHandler.java:30-45 — how handlers are invoked: wrap in `Event`, catch `RuntimeException` per handler and log, keep going
- src/main/java/io/github/aindriub/irc/client/configuration/ClientConfiguration.java:14-26, 70-110 — `eventHandlers`/`messageHandlers` lists with get/set; add a third list alongside
- src/main/java/io/github/aindriub/irc/client/configuration/ClientConfigurationBuilder.java:213-226 — `eventListener`/`messageListener` style for the new builder method
- src/main/java/io/github/aindriub/irc/client/impl/AbstractClient.java:78-81 (`shutdown`), 308-329 (`disconnect`), 334-344 (`onConnectionLost`), 346-376 (`scheduleReconnect`: give-up at 349-352, refused-server delay at 356-361, `RejectedExecutionException` at 370-373), 380-430 (tryReconnect / registration failure closes the channel and re-enters `onConnectionLost`), 426-430 (`reconnectSucceeded`)
- src/main/java/io/github/aindriub/irc/client/bot/IRCBot.java:68-94 — constructor adds its handlers to the configuration lists and overrides `onReconnected`; 243-265 — `announceReady` / `safely` pattern for fanning out to BotListeners
- src/main/java/io/github/aindriub/irc/client/configuration/ReconnectConfiguration.java — `isEnabled`, `getMaxAttempts` (0 = unlimited), `delayFor`
- src/test/java/io/github/aindriub/irc/client/impl/ReconnectTest.java, src/test/java/io/github/aindriub/irc/client/testsupport/StubIRCServer.java — how existing tests drop and restore a server connection; copy the approach into the new test class rather than editing ReconnectTest

## Acceptance
- [ ] New javadoc'd `public final class ConnectionEvent` in `event` with a nested `public enum Type { DISCONNECTED, RECONNECTING, RECONNECTED, GAVE_UP }` and getters `getType()`, `getAttempt()`, `getDelayMillis()`; `getAttempt()` is the attempt number for RECONNECTING, the number of attempts made for GAVE_UP, 0 otherwise; `getDelayMillis()` is 0 except for RECONNECTING; `ConnectionEventTest` covers every factory/getter
- [ ] `ClientConfiguration.getConnectionHandlers()` / `setConnectionHandlers(List<EventHandler<ConnectionEvent>>)` exist (javadoc'd), default to an empty mutable list; `ClientConfigurationBuilder.connectionListener(EventHandler<ConnectionEvent>)` (javadoc'd) appends to it and throws NPE naming `connectionHandler` for null
- [ ] When the server drops an established connection with reconnect enabled, subscribers receive DISCONNECTED, then RECONNECTING with `attempt=1` and `delayMillis` equal to the delay actually scheduled (`ReconnectConfiguration.delayFor(1)`, or `getMaxDelay()` after a server refusal), then RECONNECTED once registration succeeds again
- [ ] While the server stays down, each further failed attempt yields one RECONNECTING with attempt 2, 3, ...; DISCONNECTED is published exactly once per lost connection, not again when a reconnect attempt connects and then fails registration
- [ ] With `maxAttempts=2` and the server never returning, subscribers see RECONNECTING 1, RECONNECTING 2, then exactly one GAVE_UP with `getAttempt()==2`, and nothing afterwards
- [ ] With reconnect disabled, a dropped connection yields DISCONNECTED only
- [ ] `disconnect()` publishes no ConnectionEvent at all (no DISCONNECTED, no RECONNECTING); RECONNECTING is not published when scheduling is rejected because the event loop is shutting down
- [ ] A connection handler that throws `RuntimeException` is logged and does not prevent later handlers from receiving the event nor stop the reconnect from happening
- [ ] `BotListener` gains javadoc'd default no-ops `onDisconnected(IRCBot bot)`, `onReconnecting(IRCBot bot, int attempt, long delayMillis)` and `onGaveUp(IRCBot bot, int attempts)`; javadoc states they run on the event loop and must not block, and that reconnection success is still reported via `onReady`; the `onDisconnected` javadoc states explicitly that it fires only when the connection is lost, never for a deliberate `disconnect()`/shutdown, since the application already knows it asked for that
- [ ] IRCBot registers a connection handler in its constructor (same place it adds its message handlers) and calls those callbacks through `safely`; IRCBotTest shows: server drop → `onDisconnected` then `onReconnecting(bot, 1, >=0)` then `onReady`; `bot.disconnect()`/shutdown → neither `onDisconnected` nor `onReconnecting`; `maxAttempts=1` with no server → `onGaveUp(bot, 1)`
- [ ] No existing public/protected signature changed; `mvn verify` passes (97%/95% coverage gate, javadoc)

## Out of scope
- BasicIRCClient, its rejoin logic and ReconnectTest (task 04)
- IRCBotBuilder: no new builder option is needed; listeners arrive via the existing `listener(...)`
- Changing backoff, `maxAttempts` defaults, or the refused-server delay policy (#40)
- A `getConnectionState()` polling API, or reporting deliberate disconnects as events
- The initial `connect()` failing (it already throws to the caller)

package io.github.aindriub.irc.client.bot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.aindriub.irc.client.message.IRCMessage;
import io.github.aindriub.irc.client.state.ChannelState;
import io.github.aindriub.irc.client.state.UserStatus;
import io.github.aindriub.irc.client.testsupport.StubIRCServer;

public class IRCBotTest {

    private static final long TIMEOUT = 5000;

    private StubIRCServer server;
    private IRCBot bot;
    private final List<String> events = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws IOException {
        server = new StubIRCServer();
    }

    @After
    public void tearDown() throws IOException {
        if (bot != null) {
            try {
                bot.stop();
            } catch (RuntimeException e) {
                // Already down.
            }
        }
        server.close();
    }

    @Test
    public void connectsRegistersAndJoinsItsChannels() throws Exception {
        bot = builder().channels("#one", "#two").build();

        bot.start();

        assertTrue(bot.isRunning());
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        assertTrue(server.awaitLine("JOIN #one,#two", TIMEOUT));
        assertEquals(Arrays.asList("#one", "#two"), bot.getChannels());
    }

    @Test
    public void startsWithNoChannelsWithoutSendingAJoin() throws Exception {
        bot = builder().build();

        bot.start();

        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        Thread.sleep(150);
        for (String line : server.getReceived()) {
            assertFalse("nothing to join: " + line, line.startsWith("JOIN"));
        }
    }

    @Test
    public void deliversAChannelMessageToListeners() throws Exception {
        bot = builder().listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h PRIVMSG #chan :hello there");

        assertTrue(awaitEvent("message #chan someone hello there"));
    }

    @Test
    public void repliesToAChannelInThatChannel() throws Exception {
        bot = builder().listener(new BotListener() {
            @Override
            public void onMessage(MessageContext context) {
                context.reply("got it");
            }
        }).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG #chan :hello");

        assertTrue(server.awaitLine("PRIVMSG #chan :got it", before, TIMEOUT));
    }

    @Test
    public void repliesToADirectMessageToTheSenderNotToItself() throws Exception {
        bot = builder().listener(new BotListener() {
            @Override
            public void onMessage(MessageContext context) {
                assertTrue("a message to our own nick is private", context.isPrivate());
                context.reply("got it");
            }
        }).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        // Sent to the bot's nick: replying to the "target" would talk to itself.
        server.push(":someone!u@h PRIVMSG bot :hello");

        assertTrue(server.awaitLine("PRIVMSG someone :got it", before, TIMEOUT));
    }

    @Test
    public void neverDeliversTheBotsOwnMessagesBackToItself() throws Exception {
        bot = builder().listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        // Some servers echo. Dispatching this would let a replying bot loop forever.
        server.push(":bot!u@h PRIVMSG #chan :something the bot said");
        server.push(":someone!u@h PRIVMSG #chan :from someone else");

        assertTrue(awaitEvent("message #chan someone from someone else"));
        int messages = 0;
        for (String event : events) {
            if (event.startsWith("message ")) {
                messages++;
            }
        }
        assertEquals("only the other person's message", 1, messages);
    }

    @Test
    public void routesRegisteredCommands() throws Exception {
        bot = builder()
                .command("!hello", new CommandHandler() {
                    @Override
                    public void handle(MessageContext context, List<String> args) {
                        context.reply("hello " + context.getSender() + " " + args);
                    }
                })
                .build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG #chan :!hello there world");

        assertTrue(server.awaitLine("PRIVMSG #chan :hello someone [there, world]", before,
                TIMEOUT));
    }

    @Test
    public void commandMatchingIgnoresCase() throws Exception {
        bot = builder().command("!Hello", echoCommand()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG #chan :!HELLO");

        assertTrue(server.awaitLine("PRIVMSG #chan :ran", before, TIMEOUT));
    }

    @Test
    public void ignoresTextThatIsNotARegisteredCommand() throws Exception {
        bot = builder().command("!hello", echoCommand()).listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG #chan :!nosuchcommand");
        server.push(":someone!u@h PRIVMSG #chan :hello without the prefix");

        assertTrue(awaitEvent("message #chan someone hello without the prefix"));
        Thread.sleep(150);
        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            assertFalse("no command should have run: " + line, line.contains(":ran"));
        }
    }

    @Test
    public void aFailingListenerDoesNotStopTheOthersOrTheConnection() throws Exception {
        bot = builder()
                .listener(new BotListener() {
                    @Override
                    public void onMessage(MessageContext context) {
                        throw new IllegalStateException("listener is broken");
                    }
                })
                .listener(recording())
                .build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h PRIVMSG #chan :hello");

        assertTrue(awaitEvent("message #chan someone hello"));
        assertTrue("a broken listener must not drop the connection", bot.isRunning());
    }

    @Test
    public void aFailingCommandDoesNotDropTheConnection() throws Exception {
        bot = builder().command("!boom", new CommandHandler() {
            @Override
            public void handle(MessageContext context, List<String> args) {
                throw new IllegalStateException("command is broken");
            }
        }).listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h PRIVMSG #chan :!boom");

        assertTrue(awaitEvent("message #chan someone !boom"));
        assertTrue(bot.isRunning());
    }

    @Test
    public void reportsJoinPartAndQuit() throws Exception {
        bot = builder().listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h JOIN #chan");
        server.push(":someone!u@h PART #chan");
        server.push(":someone!u@h QUIT :bye now");

        assertTrue(awaitEvent("join #chan someone"));
        assertTrue(awaitEvent("part #chan someone"));
        assertTrue(awaitEvent("quit someone bye now"));
    }

    @Test
    public void noticesAreDeliveredSeparatelyFromMessages() throws Exception {
        bot = builder().listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h NOTICE #chan :automated thing");

        assertTrue(awaitEvent("notice #chan someone automated thing"));
    }

    @Test
    public void announcesReadyOnceItIsInItsChannels() throws Exception {
        bot = builder().channels("#one").listener(recording()).build();

        bot.start();

        assertTrue(awaitEvent("ready"));
        assertTrue("ready means joined", server.awaitLine("JOIN #one", TIMEOUT));
    }

    @Test
    public void learnsTheNickTheServerActuallyGaveIt() throws Exception {
        bot = builder().build();
        bot.start();

        // The stub welcomes us as "bot"; a collision would have changed it.
        assertEquals("bot", bot.getNick());
    }

    @Test
    public void sayAndNoticeAndJoinAndPartReachTheServer() throws Exception {
        bot = builder().build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        bot.say("#chan", "spoken");
        bot.notice("#chan", "noticed");
        bot.join("#later");
        bot.part("#later");

        assertTrue(server.awaitLine("PRIVMSG #chan :spoken", before, TIMEOUT));
        assertTrue(server.awaitLine("NOTICE #chan :noticed", before, TIMEOUT));
        assertTrue(server.awaitLine("JOIN #later", before, TIMEOUT));
        assertTrue(server.awaitLine("PART #later", before, TIMEOUT));
    }

    @Test
    public void splitsAMessageTooLongForOneLine() throws Exception {
        bot = builder().build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            longText.append("the quick brown fox jumps over the lazy dog ");
        }
        bot.say("#chan", longText.toString().trim());

        // The server would truncate one over-long line rather than refuse it, so
        // the tail would vanish and the head would look deliberate.
        assertTrue(server.awaitLine("PRIVMSG #chan :the quick", before, TIMEOUT));
        Thread.sleep(300);
        java.util.List<String> sent = new java.util.ArrayList<>();
        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            if (line.startsWith("PRIVMSG #chan :")) {
                sent.add(line);
                assertTrue("a sent line was " + line.length() + " chars, over the limit",
                        line.getBytes("UTF-8").length + 2 <= 512);
            }
        }
        assertTrue("expected several messages, got " + sent.size(), sent.size() > 1);
    }

    @Test
    public void moderationHelpersReachTheServer() throws Exception {
        bot = builder().build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        bot.kick("#chan", "someone", "behave");
        bot.topic("#chan", "a new topic");
        bot.mode("#chan", "+o", "someone");
        bot.invite("someone", "#chan");
        bot.away("making tea");
        bot.back();

        assertTrue(server.awaitLine("KICK #chan someone :behave", before, TIMEOUT));
        assertTrue(server.awaitLine("TOPIC #chan :a new topic", before, TIMEOUT));
        assertTrue(server.awaitLine("MODE #chan +o someone", before, TIMEOUT));
        assertTrue(server.awaitLine("INVITE someone #chan", before, TIMEOUT));
        assertTrue(server.awaitLine("AWAY :making tea", before, TIMEOUT));
        assertTrue(server.awaitLine("AWAY", before, TIMEOUT));
    }

    @Test
    public void exposesTheUnderlyingClientAndRawMessage() throws Exception {
        final List<IRCMessage> raws = new ArrayList<>();
        bot = builder().listener(new BotListener() {
            @Override
            public void onMessage(MessageContext context) {
                raws.add(context.getRaw());
                events.add("raw");
            }
        }).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push("@mod=1 :someone!u@h PRIVMSG #chan :tagged");

        assertTrue(awaitEvent("raw"));
        assertEquals("1", raws.get(0).getTags().get("mod"));
        assertTrue(bot.getClient().isRegistered());
    }

    @Test
    public void repliesDirectlyToTheSenderOfAChannelMessageWhenAsked() throws Exception {
        bot = builder().listener(new BotListener() {
            @Override
            public void onMessage(MessageContext context) {
                context.replyDirectly("just for you");
                context.replyNotice("quietly");
            }
        }).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG #chan :hello");

        assertTrue("replyDirectly goes to the person, not the channel",
                server.awaitLine("PRIVMSG someone :just for you", before, TIMEOUT));
        assertTrue("replyNotice goes back to the channel as a NOTICE",
                server.awaitLine("NOTICE #chan :quietly", before, TIMEOUT));
    }

    @Test
    public void theContextDescribesItselfAndExposesTheBot() throws Exception {
        final List<MessageContext> captured = new ArrayList<>();
        bot = builder().listener(new BotListener() {
            @Override
            public void onMessage(MessageContext context) {
                captured.add(context);
                events.add("captured");
            }
        }).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h PRIVMSG #chan :hello");
        assertTrue(awaitEvent("captured"));

        MessageContext context = captured.get(0);
        assertEquals(bot, context.getBot());
        assertEquals("#chan", context.getReplyTarget());
        assertFalse(context.isPrivate());
        assertEquals("<someone -> #chan> hello", context.toString());
    }

    @Test
    public void aBareListenerHandlesEverythingWithoutOverridingAnything() throws Exception {
        // Every callback defaults to doing nothing, so a listener can override one.
        bot = builder().listener(new BotListener() {
        }).listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h PRIVMSG #chan :hi");
        server.push(":someone!u@h NOTICE #chan :hi");
        server.push(":someone!u@h JOIN #chan");
        server.push(":someone!u@h PART #chan");
        server.push(":someone!u@h QUIT :bye");
        server.push(":server 372 bot :message of the day");

        assertTrue(awaitEvent("quit someone bye"));
        assertTrue(bot.isRunning());
    }

    @Test
    public void forwardsUnhandledMessagesToOnOther() throws Exception {
        final List<IRCMessage> others = new ArrayList<>();
        bot = builder().listener(new BotListener() {
            @Override
            public void onOther(IRCBot bot, IRCMessage message) {
                others.add(message);
                events.add("other " + message.getCommand());
            }
        }).build();
        bot.start();

        server.push(":server 372 bot :message of the day");

        assertTrue(awaitEvent("other 372"));
        assertTrue("numerics reach onOther as well", others.size() >= 1);
    }

    @Test
    public void followsItsOwnNickChange() throws Exception {
        bot = builder().build();
        bot.start();
        assertEquals("bot", bot.getNick());

        server.push(":bot!u@h NICK :bot2");
        long deadline = System.currentTimeMillis() + TIMEOUT;
        while (!"bot2".equals(bot.getNick()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        assertEquals("replies must go out under the nick we actually have", "bot2",
                bot.getNick());

        // And a rename by someone else must not be mistaken for our own.
        server.push(":other!u@h NICK :other2");
        Thread.sleep(150);
        assertEquals("bot2", bot.getNick());
    }

    @Test
    public void sendsTheConfiguredPassword() throws Exception {
        bot = builder().password("hunter2").build();

        bot.start();

        assertTrue(server.awaitLine("PASS hunter2", TIMEOUT));
    }

    @Test
    public void defaultsToTheConventionalTlsPort() {
        // Built, not started: 6697 is simply the port it would dial.
        IRCBot.builder().host("irc.example.org").nick("bot").build();
    }

    @Test
    public void listenersCanBeAddedAfterConstruction() throws Exception {
        bot = builder().build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        bot.addListener(recording());
        server.push(":someone!u@h PRIVMSG #chan :late listener");

        assertTrue(awaitEvent("message #chan someone late listener"));
    }

    @Test
    public void announcesReadyAgainAfterReconnecting() throws Exception {
        IRCBotBuilder builder = IRCBot.builder().host("127.0.0.1").port(server.getPort())
                .nick("bot").channels("#one").listener(recording());
        builder.client().secure(false).reconnect(true).reconnectBackoff(30, 60, 0)
                .registrationTimeout(3000);
        bot = builder.build();
        bot.start();
        assertTrue(awaitEvent("ready"));
        events.clear();
        int before = server.receivedCount();

        server.dropConnection();

        assertTrue("a bot needs to know it is usable again", awaitEvent("ready"));
        assertTrue("and it should be back in its channels",
                server.awaitLine("JOIN #one", before, TIMEOUT));
    }

    @Test
    public void tracksWhoIsInAChannel() throws Exception {
        bot = builder().channels("#chan").build();
        bot.start();
        assertTrue(server.awaitLine("JOIN #chan", TIMEOUT));

        server.push(":server 353 bot = #chan :@alice +bob bot");
        server.push(":server 366 bot #chan :End of /NAMES list");
        long deadline = System.currentTimeMillis() + TIMEOUT;
        while (bot.getChannelState("#chan") == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        ChannelState channel = bot.getChannelState("#chan");
        assertEquals(3, channel.size());
        assertTrue(channel.getUser("alice").isOperator());
        assertTrue(channel.getUser("bob").hasStatus(UserStatus.VOICE));

        server.push(":dave!u@h JOIN #chan");
        deadline = System.currentTimeMillis() + TIMEOUT;
        while (!channel.contains("dave") && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(channel.contains("dave"));
    }

    @Test
    public void channelTrackingCanBeSwitchedOff() throws Exception {
        bot = builder().channels("#chan").trackChannelState(false).build();
        bot.start();
        assertTrue(server.awaitLine("JOIN #chan", TIMEOUT));

        server.push(":server 353 bot = #chan :@alice bot");
        Thread.sleep(200);

        assertNull("a bot that never asks should not pay for the bookkeeping",
                bot.getChannelState("#chan"));
        assertNull(bot.getChannelStateTracker());
    }

    @Test
    public void theTrackerFollowsTheNickTheServerGaveUs() throws Exception {
        bot = builder().build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        // Both must agree, or the tracker cannot tell our own PART from anyone else's.
        assertEquals(bot.getNick(), bot.getChannelStateTracker().getSelfNick());
    }

    @Test
    public void ignoresItsOwnNotices() throws Exception {
        bot = builder().listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":bot!u@h NOTICE #chan :our own notice");
        server.push(":someone!u@h NOTICE #chan :someone else's");

        assertTrue(awaitEvent("notice #chan someone someone else's"));
        int notices = 0;
        for (String event : events) {
            if (event.startsWith("notice ")) {
                notices++;
            }
        }
        assertEquals(1, notices);
    }

    @Test
    public void copesWithAWelcomeThatNamesNoNick() throws Exception {
        bot = builder().listener(recording()).build();
        bot.start();

        // Malformed, but it must not overwrite the nick with null.
        server.push(":server 001");
        Thread.sleep(150);

        assertEquals("bot", bot.getNick());
    }

    @Test
    public void forwardsUnknownNonNumericCommandsToOnOther() throws Exception {
        bot = builder().listener(new BotListener() {
            @Override
            public void onOther(IRCBot bot, IRCMessage message) {
                events.add("other " + message.getCommand());
            }
        }).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":admin!u@h WALLOPS :server going down");

        assertTrue(awaitEvent("other WALLOPS"));
    }

    @Test
    public void requiresANick() {
        try {
            IRCBot.builder().host("127.0.0.1").port(6667).build();
            fail("expected a nick to be required");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("nick"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void commandsMustCarryThePrefix() {
        IRCBot.builder().command("hello", echoCommand());
    }

    @Test(expected = IllegalArgumentException.class)
    public void theCommandPrefixMustNotBeBlank() {
        IRCBot.builder().commandPrefix("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void theCommandPrefixMustNotBeNull() {
        IRCBot.builder().commandPrefix(null);
    }

    @Test
    public void isNotRunningBeforeItStarts() {
        bot = builder().build();

        assertFalse(bot.isRunning());
    }

    @Test
    public void deliversAServerSentMessageThatHasNoNick() throws Exception {
        bot = builder().listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        // A server prefix has no nick part, so the self check must cope with null.
        server.push(":irc.example.org PRIVMSG #chan :a server notice of sorts");

        assertTrue(awaitEvent("message #chan null a server notice of sorts"));
    }

    @Test(expected = IllegalStateException.class)
    public void theCommandPrefixMustBeSetBeforeAnyCommand() {
        IRCBot.builder().command("!hello", echoCommand()).commandPrefix("~");
    }

    @Test
    public void supportsAnAlternativeCommandPrefix() throws Exception {
        bot = builder().commandPrefix("~").command("~hello", echoCommand()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG #chan :~hello");

        assertTrue(server.awaitLine("PRIVMSG #chan :ran", before, TIMEOUT));
    }

    @Test
    public void messageContextExposesCtcpAccessors() throws Exception {
        final List<MessageContext> captured = new ArrayList<>();
        bot = builder().listener(new BotListener() {
            @Override
            public void onMessage(MessageContext context) {
                captured.add(context);
                events.add("captured " + captured.size());
            }
        }).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h PRIVMSG bot :\u0001ACTION waves\u0001");
        assertTrue(awaitEvent("captured 1"));
        MessageContext action = captured.get(0);
        assertTrue(action.isCtcp());
        assertEquals("ACTION", action.getCtcpCommand());
        assertEquals("waves", action.getCtcpArgument());

        server.push(":someone!u@h PRIVMSG bot :just chatting");
        assertTrue(awaitEvent("captured 2"));
        MessageContext plain = captured.get(1);
        assertFalse(plain.isCtcp());
        assertNull(plain.getCtcpCommand());
        assertNull(plain.getCtcpArgument());
    }

    @Test
    public void messageContextExposesPlainTextWithGetTextUnchanged() throws Exception {
        final List<MessageContext> captured = new ArrayList<>();
        bot = builder().listener(new BotListener() {
            @Override
            public void onMessage(MessageContext context) {
                captured.add(context);
                events.add("captured");
            }
        }).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h PRIVMSG #chan :\u0002bold\u0002 text");
        assertTrue(awaitEvent("captured"));

        MessageContext context = captured.get(0);
        assertEquals("\u0002bold\u0002 text", context.getText());
        assertEquals("bold text", context.getPlainText());
    }

    @Test
    public void actionSendsACtcpAction() throws Exception {
        bot = builder().build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        bot.action("#chan", "waves");

        assertTrue(server.awaitLine("PRIVMSG #chan :\u0001ACTION waves\u0001", before, TIMEOUT));
    }

    @Test
    public void actionSplitsLongTextIntoCompleteActionsPerLine() throws Exception {
        bot = builder().build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            longText.append("the quick brown fox jumps over the lazy dog ");
        }
        bot.action("#chan", longText.toString().trim());

        assertTrue(server.awaitLine("PRIVMSG #chan :\u0001ACTION the quick", before, TIMEOUT));
        Thread.sleep(300);
        List<String> sent = new ArrayList<>();
        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            if (line.startsWith("PRIVMSG #chan :")) {
                sent.add(line);
                assertTrue("each line is a complete action",
                        line.startsWith("PRIVMSG #chan :\u0001ACTION ") && line.endsWith("\u0001"));
                assertTrue("a sent line was " + line.length() + " chars, over the limit",
                        line.getBytes("UTF-8").length + 2 <= 512);
            }
        }
        assertTrue("expected several messages, got " + sent.size(), sent.size() > 1);
    }

    @Test
    public void respondsToCtcpVersionPingAndTimeWhenEnabled() throws Exception {
        bot = builder().respondToCtcp(true).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG bot :\u0001VERSION\u0001");
        server.push(":someone!u@h PRIVMSG bot :\u0001PING 123\u0001");
        server.push(":someone!u@h PRIVMSG bot :\u0001TIME\u0001");

        assertTrue(server.awaitLine("NOTICE someone :\u0001PING 123\u0001", before, TIMEOUT));
        boolean sawVersion = false;
        boolean sawTime = false;
        Thread.sleep(200);
        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            if (line.startsWith("NOTICE someone :\u0001VERSION ") && line.endsWith("\u0001")) {
                assertTrue("non-empty version", line.length() > "NOTICE someone :\u0001VERSION \u0001".length());
                sawVersion = true;
            }
            if (line.startsWith("NOTICE someone :\u0001TIME ") && line.endsWith("\u0001")) {
                assertTrue("non-empty time", line.length() > "NOTICE someone :\u0001TIME \u0001".length());
                sawTime = true;
            }
        }
        assertTrue(sawVersion);
        assertTrue(sawTime);
    }

    @Test
    public void doesNotRespondToCtcpByDefault() throws Exception {
        bot = builder().build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG bot :\u0001VERSION\u0001");
        Thread.sleep(200);

        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            assertFalse("no CTCP reply expected", line.startsWith("NOTICE"));
        }
    }

    @Test
    public void doesNotRespondToCtcpReceivedAsNotice() throws Exception {
        bot = builder().respondToCtcp(true).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h NOTICE bot :\u0001VERSION\u0001");
        Thread.sleep(200);

        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            assertFalse("a CTCP NOTICE must never be answered", line.startsWith("NOTICE"));
        }
    }

    @Test
    public void doesNotRespondToCtcpFromSelf() throws Exception {
        bot = builder().respondToCtcp(true).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":bot!u@h PRIVMSG bot :\u0001VERSION\u0001");
        Thread.sleep(200);

        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            assertFalse("must not answer its own CTCP", line.startsWith("NOTICE"));
        }
    }

    @Test
    public void doesNotRespondToActionOrUnknownCtcp() throws Exception {
        bot = builder().respondToCtcp(true).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG bot :\u0001ACTION waves\u0001");
        server.push(":someone!u@h PRIVMSG bot :\u0001UNKNOWNTHING\u0001");
        Thread.sleep(200);

        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            assertFalse("no reply for ACTION or unknown CTCP", line.startsWith("NOTICE"));
        }
    }

    @Test
    public void ctcpPrivmsgsStillReachOnMessageWithTextUnchangedResponderOff() throws Exception {
        bot = builder().listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h PRIVMSG #chan :\u0001VERSION\u0001");

        assertTrue(awaitEvent("message #chan someone \u0001VERSION\u0001"));
    }

    @Test
    public void ctcpPrivmsgsStillReachOnMessageWithTextUnchangedResponderOn() throws Exception {
        bot = builder().respondToCtcp(true).listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h PRIVMSG #chan :\u0001VERSION\u0001");

        assertTrue(awaitEvent("message #chan someone \u0001VERSION\u0001"));
    }

    @Test
    public void dispatchesCommandsAfterStrippingFormatting() throws Exception {
        bot = builder().command("!hello", new CommandHandler() {
            @Override
            public void handle(MessageContext context, List<String> args) {
                context.reply("hello " + args);
            }
        }).listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG #chan :\u0002!hello\u0002 world");

        assertTrue(server.awaitLine("PRIVMSG #chan :hello [world]", before, TIMEOUT));
    }

    @Test
    public void anActionDoesNotDispatchAsACommand() throws Exception {
        bot = builder().command("!hello", echoCommand()).listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG #chan :\u0001ACTION !hello\u0001");
        assertTrue(awaitEvent("message #chan someone \u0001ACTION !hello\u0001"));

        Thread.sleep(150);
        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            assertFalse("no command should have run: " + line, line.contains(":ran"));
        }
    }

    @Test
    public void aServerPrefixedCtcpStillReachesOnMessageAndSendsNothing() throws Exception {
        bot = builder().respondToCtcp(true).listener(recording()).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":irc.example.net PRIVMSG bot :\u0001VERSION\u0001");

        assertTrue(awaitEvent("message bot null \u0001VERSION\u0001"));
        Thread.sleep(150);
        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            assertFalse("no reply for a server-prefixed CTCP", line.startsWith("NOTICE"));
        }
    }

    @Test
    public void aCtcpReplyThatWouldNotFitInOneLineIsDropped() throws Exception {
        bot = builder().respondToCtcp(true).build();
        bot.start();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            huge.append('1');
        }
        server.push(":someone!u@h PRIVMSG bot :\u0001PING " + huge + "\u0001");
        Thread.sleep(200);

        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            assertFalse("an over-long CTCP reply must not be split or sent: " + line,
                    line.startsWith("NOTICE"));
        }
    }

    private IRCBotBuilder builder() {
        IRCBotBuilder builder = IRCBot.builder().host("127.0.0.1").port(server.getPort())
                .nick("bot");
        builder.client().secure(false).reconnect(false).registrationTimeout(3000);
        return builder;
    }

    private CommandHandler echoCommand() {
        return new CommandHandler() {
            @Override
            public void handle(MessageContext context, List<String> args) {
                context.reply("ran");
            }
        };
    }

    private BotListener recording() {
        return new BotListener() {
            @Override
            public void onMessage(MessageContext context) {
                events.add("message " + context.getTarget() + " " + context.getSender() + " "
                        + context.getText());
            }

            @Override
            public void onNotice(MessageContext context) {
                events.add("notice " + context.getTarget() + " " + context.getSender() + " "
                        + context.getText());
            }

            @Override
            public void onJoin(IRCBot bot, String channel, String nick) {
                events.add("join " + channel + " " + nick);
            }

            @Override
            public void onPart(IRCBot bot, String channel, String nick) {
                events.add("part " + channel + " " + nick);
            }

            @Override
            public void onQuit(IRCBot bot, String nick, String reason) {
                events.add("quit " + nick + " " + reason);
            }

            @Override
            public void onReady(IRCBot bot) {
                events.add("ready");
            }
        };
    }

    private boolean awaitEvent(String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT;
        while (System.currentTimeMillis() < deadline) {
            if (events.contains(expected)) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }
}

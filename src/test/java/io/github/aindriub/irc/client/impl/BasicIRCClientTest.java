package io.github.aindriub.irc.client.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.aindriub.irc.client.command.Join;
import io.github.aindriub.irc.client.command.Part;
import io.github.aindriub.irc.client.configuration.ClientConfiguration;
import io.github.aindriub.irc.client.configuration.ClientConfigurationBuilder;
import io.github.aindriub.irc.client.event.MessageListener;
import io.github.aindriub.irc.client.message.IRCMessage;
import io.github.aindriub.irc.client.state.CaseMapping;
import io.github.aindriub.irc.client.testsupport.StubIRCServer;

/**
 * Exercises how BasicIRCClient learns about kicks and case mapping from inbound
 * traffic, against the in-process stub rather than the reconnect flow in
 * {@link ReconnectTest}.
 */
public class BasicIRCClientTest {

    private static final long TIMEOUT = 5000;

    private StubIRCServer server;
    private BasicIRCClient client;

    @Before
    public void setUp() throws IOException {
        server = new StubIRCServer();
    }

    @After
    public void tearDown() throws IOException {
        if (client != null) {
            try {
                client.disconnect();
            } catch (RuntimeException e) {
                // Already down; the test has made its point.
            }
        }
        server.close();
    }

    @Test
    public void aKickOfAnotherNickLeavesTheChannelJoined() throws Exception {
        client = client();
        client.connect();
        client.sendCommand(new Join(Arrays.asList("#chan", "#sentinel")));
        assertTrue(server.awaitLine("JOIN #chan,#sentinel", TIMEOUT));

        server.push(":op!u@h KICK #chan someoneelse :reason");
        // A second, unambiguous kick of self on another channel: waiting for its
        // deterministic effect proves the first line was processed too, since a
        // single connection handles lines in order, without sleeping blind.
        server.push(":op!u@h KICK #sentinel bot :reason");

        waitUntil(TIMEOUT, new Condition() {
            @Override
            public boolean met() {
                return !client.getJoinedChannels().contains("#sentinel");
            }
        });
        assertEquals(Arrays.asList("#chan"), client.getJoinedChannels());
    }

    @Test
    public void selfFollowsANickChangeAndAKickOfTheNewNickDropsTheChannel() throws Exception {
        client = client();
        client.connect();
        client.sendCommand(new Join("#chan"));
        assertTrue(server.awaitLine("JOIN #chan", TIMEOUT));

        // What a collision retry during registration would have left the client
        // as, simulated directly: RPL_WELCOME already settled self on "bot".
        server.push(":bot!u@h NICK nick_");
        server.push(":op!u@h KICK #chan nick_ :reason");

        waitUntilEmpty(TIMEOUT);
        assertTrue(client.getJoinedChannels().isEmpty());
    }

    @Test
    public void caseMappingRfc1459FoldsBracketsSoAKickDropsTheChannel() throws Exception {
        client = client();
        client.connect();
        server.push(":bot!u@h NICK nick[1]");
        client.sendCommand(new Join("#chan{x}"));
        assertTrue(server.awaitLine("JOIN #chan{x}", TIMEOUT));
        server.push(":stub 005 nick[1] CASEMAPPING=rfc1459 :are supported by this server");

        server.push(":op!u@h KICK #Chan[x] NICK{1} :reason");

        waitUntilEmpty(TIMEOUT);
        assertTrue(client.getJoinedChannels().isEmpty());
    }

    @Test
    public void caseMappingAsciiDoesNotFoldBracketsSoTheChannelIsKept() throws Exception {
        client = client();
        client.connect();
        server.push(":bot!u@h NICK nick[1]");
        client.sendCommand(new Join(Arrays.asList("#chan{x}", "#sentinel")));
        assertTrue(server.awaitLine("JOIN #chan{x},#sentinel", TIMEOUT));
        server.push(":stub 005 nick[1] CASEMAPPING=ascii :are supported by this server");

        server.push(":op!u@h KICK #Chan[x] NICK{1} :reason");
        // A second kick that matches self exactly, so waiting for its deterministic
        // effect proves the ascii-mismatched kick above was already processed,
        // without sleeping blind.
        server.push(":op!u@h KICK #sentinel nick[1] :reason");

        waitUntil(TIMEOUT, new Condition() {
            @Override
            public boolean met() {
                return !client.getJoinedChannels().contains("#sentinel");
            }
        });
        assertEquals(Arrays.asList("#chan{x}"), client.getJoinedChannels());
    }

    @Test
    public void getCaseMappingIsRfc1459BeforeIsupport() throws Exception {
        client = client();
        client.connect();

        assertEquals(CaseMapping.RFC1459, client.getCaseMapping());
    }

    @Test
    public void getCaseMappingIsTheAdvertisedMappingAfterIsupport() throws Exception {
        client = client();
        client.connect();

        server.push(":stub 005 bot CASEMAPPING=ascii :are supported by this server");
        waitUntil(TIMEOUT, new Condition() {
            @Override
            public boolean met() {
                return client.getCaseMapping() == CaseMapping.ASCII;
            }
        });
        assertEquals(CaseMapping.ASCII, client.getCaseMapping());
    }

    @Test
    public void getCaseMappingIsAsciiForAnUnrecognisedValue() throws Exception {
        client = client();
        client.connect();

        server.push(":stub 005 bot CASEMAPPING=rfc8265 :are supported by this server");
        waitUntil(TIMEOUT, new Condition() {
            @Override
            public boolean met() {
                return client.getCaseMapping() == CaseMapping.ASCII;
            }
        });
        assertEquals(CaseMapping.ASCII, client.getCaseMapping());
    }

    @Test
    public void kickTrackingWorksWithNoConfiguredMessageHandlers() throws Exception {
        ClientConfiguration configuration = new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(false)
                .registrationTimeout(3000)
                .build();
        int before = configuration.getMessageHandlers().size();
        client = new BasicIRCClient(configuration);
        client.connect();
        client.sendCommand(new Join("#chan"));
        assertTrue(server.awaitLine("JOIN #chan", TIMEOUT));

        server.push(":op!u@h KICK #chan bot :reason");

        waitUntilEmpty(TIMEOUT);
        assertTrue(client.getJoinedChannels().isEmpty());
        assertEquals("no entries added to the caller's own list", before,
                configuration.getMessageHandlers().size());
    }

    @Test
    public void kickTrackingRunsBeforeAConfiguredMessageListenerSeesIt() throws Exception {
        final List<List<String>> seenInsideOnKick = new ArrayList<>();
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(false)
                .registrationTimeout(3000)
                .messageListener(new MessageListener() {
                    @Override
                    protected void onKick(String channel, String kicked, String by,
                            IRCMessage raw) {
                        synchronized (seenInsideOnKick) {
                            seenInsideOnKick.add(client.getJoinedChannels());
                            seenInsideOnKick.notifyAll();
                        }
                    }
                })
                .build());
        client.connect();
        client.sendCommand(new Join("#chan"));
        assertTrue(server.awaitLine("JOIN #chan", TIMEOUT));

        server.push(":op!u@h KICK #chan bot :reason");

        synchronized (seenInsideOnKick) {
            long deadline = System.currentTimeMillis() + TIMEOUT;
            while (seenInsideOnKick.isEmpty() && System.currentTimeMillis() < deadline) {
                seenInsideOnKick.wait(TIMEOUT);
            }
            assertTrue("expected the listener to run", !seenInsideOnKick.isEmpty());
            assertTrue("channel should already be gone inside onKick",
                    seenInsideOnKick.get(0).isEmpty());
        }
    }

    @Test
    public void outboundTrackingKeysChannelsWithTheCurrentCaseMapping() throws Exception {
        client = client();
        client.connect();
        client.sendCommand(new Join("#A"));
        assertTrue(server.awaitLine("JOIN #A", TIMEOUT));

        client.sendCommand(new Part("#a"));

        assertTrue("case-folded PART should have dropped #A too",
                client.getJoinedChannels().isEmpty());
    }

    @Test
    public void joiningACaseFoldedDuplicateReplacesRatherThanDuplicatesTheEntry() throws Exception {
        client = client();
        client.connect();
        client.sendCommand(new Join("#A"));
        assertTrue(server.awaitLine("JOIN #A", TIMEOUT));

        client.sendCommand(new Join("#a"));

        assertEquals("re-joining under a different case must not track it twice",
                Arrays.asList("#a"), client.getJoinedChannels());
    }

    @Test
    public void selfIsTheNickFromRplWelcomeAfterACollisionRetry() throws Exception {
        server.withholdWelcome(true);
        client = client();
        Thread connecting = new Thread(new Runnable() {
            @Override
            public void run() {
                client.connect();
            }
        });
        connecting.start();

        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        // Simulate the server settling the client on a different nick than the one
        // it asked for, the way a collision retry during registration would.
        server.push(":stub 001 bot_ :Welcome to the stub");
        connecting.join(TIMEOUT);
        assertTrue(client.isRegistered());

        client.sendCommand(new Join("#chan"));
        assertTrue(server.awaitLine("JOIN #chan", TIMEOUT));

        server.push(":op!u@h KICK #chan bot_ :reason");

        waitUntilEmpty(TIMEOUT);
        assertTrue(client.getJoinedChannels().isEmpty());
    }

    @Test
    public void aNegatedCasemappingTokenResetsToTheDefault() throws Exception {
        client = client();
        client.connect();
        server.push(":stub 005 bot CASEMAPPING=ascii :are supported by this server");
        waitUntil(TIMEOUT, new Condition() {
            @Override
            public boolean met() {
                return client.getCaseMapping() == CaseMapping.ASCII;
            }
        });

        server.push(":stub 005 bot -CASEMAPPING :are supported by this server");

        waitUntil(TIMEOUT, new Condition() {
            @Override
            public boolean met() {
                return client.getCaseMapping() == CaseMapping.RFC1459;
            }
        });
        assertEquals(CaseMapping.RFC1459, client.getCaseMapping());
    }

    private void waitUntilEmpty(long timeoutMillis) throws InterruptedException {
        waitUntil(timeoutMillis, new Condition() {
            @Override
            public boolean met() {
                return client.getJoinedChannels().isEmpty();
            }
        });
    }

    private void waitUntil(long timeoutMillis, Condition condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!condition.met() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        if (!condition.met()) {
            fail("condition was not met within " + timeoutMillis + "ms");
        }
    }

    private interface Condition {
        boolean met();
    }

    private BasicIRCClient client() {
        return new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(false)
                .registrationTimeout(3000)
                .build());
    }
}

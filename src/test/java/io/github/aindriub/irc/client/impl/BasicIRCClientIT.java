package io.github.aindriub.irc.client.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import org.junit.Before;
import org.junit.Test;

import io.github.aindriub.irc.client.configuration.ClientConfigurationBuilder;
import io.github.aindriub.irc.client.event.LoggingEventHandler;

/**
 * Talks to a real server, so it runs only under: mvn verify -Pintegration-test
 *
 * <p>Twitch needs a token, supplied as -Dirc.it.nick=... -Dirc.it.token=oauth:...
 */
public class BasicIRCClientIT {

    private String nick;
    private String token;
    private BasicIRCClient client;

    @Before
    public void setUp() {
        nick = System.getProperty("irc.it.nick");
        token = System.getProperty("irc.it.token");
        assumeNotNull(nick, token);

        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("irc.chat.twitch.tv")
                .port(6697)
                .secure(true)
                .keepAlive(true)
                .nick(nick)
                .password(token)
                .capability("twitch.tv/tags")
                .capability("twitch.tv/commands")
                .eventListener(new LoggingEventHandler())
                .outputStream(System.out)
                .build());
    }

    @Test
    public void registersAndDisconnects() {
        // connect() returns only once the server has accepted registration.
        client.connect();

        assertTrue(client.isConnected());
        assertTrue(client.isRegistered());

        client.disconnect();

        assertFalse(client.isConnected());
    }
}

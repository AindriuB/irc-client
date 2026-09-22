package io.github.aindriub.irc.client.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import io.github.aindriub.irc.client.configuration.ClientConfigurationBuilder;
import io.github.aindriub.irc.client.event.LoggingEventHandler;

/**
 * Talks to a real server, so it runs only under: mvn verify -Pintegration-test
 */
public class BasicIRCClientIT {

    private BasicIRCClient client;

    @Before
    public void setUp() {
        ClientConfigurationBuilder cb = new ClientConfigurationBuilder();
        cb.host("irc.chat.twitch.tv")
                .port(6697)
                .secure(true)
                .keepAlive(true);

        cb.eventListener(new LoggingEventHandler());
        cb.outputStream(System.out);
        client = new BasicIRCClient(cb.build());
    }

    @Test
    public void connectsAndDisconnects() {
        client.connect();
        assertTrue(client.isConnected());

        client.disconnect();
        assertFalse(client.isConnected());
    }
}

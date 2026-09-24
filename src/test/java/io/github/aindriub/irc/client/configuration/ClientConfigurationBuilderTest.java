package io.github.aindriub.irc.client.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.github.aindriub.irc.client.event.ConnectionEvent;
import io.github.aindriub.irc.client.event.Event;
import io.github.aindriub.irc.client.event.EventHandler;

public class ClientConfigurationBuilderTest {

    @Test
    public void buildsAConnection() {
        ClientConfiguration configuration = new ClientConfigurationBuilder()
                .host("irc.example.org")
                .port(6697)
                .secure(false)
                .keepAlive(false)
                .build();

        assertEquals("irc.example.org", configuration.getConnection().getHost());
        assertEquals(6697, configuration.getConnection().getPort());
        assertFalse(configuration.getConnection().isSecure());
        assertFalse(configuration.getConnection().isKeepAlive());
    }

    @Test
    public void defaultsToSecureAndValidatedCertificates() {
        ConnectionConfiguration connection = new ConnectionConfiguration();
        assertTrue(connection.isSecure());
        assertFalse(connection.isTrustAllCertificates());
    }

    @Test(expected = IllegalStateException.class)
    public void refusesToSilentlyDiscardPerFieldConnectionSettings() {
        new ClientConfigurationBuilder()
                .host("irc.example.org")
                .connection(new ConnectionConfiguration());
    }

    @Test(expected = IllegalStateException.class)
    public void requiresAHost() {
        new ClientConfigurationBuilder().port(6697).build();
    }

    @Test(expected = IllegalStateException.class)
    public void requiresAPort() {
        new ClientConfigurationBuilder().host("irc.example.org").build();
    }

    @Test
    public void connectionListenerIsDefaultEmptyAndAppendable() {
        EventHandler<ConnectionEvent> handler = new EventHandler<ConnectionEvent>() {
            @Override
            public void publishEvent(Event<ConnectionEvent> event) {
            }
        };

        ClientConfiguration configuration = new ClientConfigurationBuilder()
                .host("irc.example.org")
                .port(6697)
                .connectionListener(handler)
                .build();

        assertEquals(1, configuration.getConnectionHandlers().size());
        assertTrue(configuration.getConnectionHandlers().contains(handler));
    }

    @Test
    public void defaultsToAnEmptyMutableConnectionHandlerList() {
        ClientConfiguration configuration = new ClientConfiguration();

        assertTrue(configuration.getConnectionHandlers().isEmpty());
        // Must not throw: the list has to be mutable, not an immutable default.
        configuration.getConnectionHandlers().add(new EventHandler<ConnectionEvent>() {
            @Override
            public void publishEvent(Event<ConnectionEvent> event) {
            }
        });
    }

    @Test
    public void connectionListenerRejectsNull() {
        try {
            new ClientConfigurationBuilder().connectionListener(null);
            fail("expected a NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("connectionHandler"));
        }
    }
}

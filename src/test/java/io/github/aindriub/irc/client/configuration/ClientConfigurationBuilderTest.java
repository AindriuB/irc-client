package io.github.aindriub.irc.client.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}

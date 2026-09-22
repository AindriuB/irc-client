package io.github.aindriub.irc.client.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import io.github.aindriub.irc.client.event.Event;
import io.github.aindriub.irc.client.event.EventHandler;
import io.github.aindriub.irc.client.message.IRCMessage;

/**
 * Round-trips the configuration beans. Worth doing rather than assuming: this
 * codebase shipped a Nick command that sent PART and a duplicated
 * setOutputStream/setOutputStreams pair, both plain copy-paste slips.
 */
public class ConfigurationTest {

    @Test
    public void clientConfigurationRoundTrips() {
        ClientConfiguration configuration = new ClientConfiguration();
        Charset utf16 = Charset.forName("UTF-16");
        ConnectionConfiguration connection = new ConnectionConfiguration();
        RegistrationConfiguration registration = new RegistrationConfiguration();
        ReconnectConfiguration reconnect = new ReconnectConfiguration();
        List<EventHandler<String>> raw = new ArrayList<>();
        List<EventHandler<IRCMessage>> typed = new ArrayList<>();
        List<OutputStream> streams = Arrays.<OutputStream>asList(new ByteArrayOutputStream());

        configuration.setCharSet(utf16);
        configuration.setDebug(true);
        configuration.setConnection(connection);
        configuration.setRegistration(registration);
        configuration.setReconnect(reconnect);
        configuration.setEventHandlers(raw);
        configuration.setMessageHandlers(typed);
        configuration.setOutputStreams(streams);

        assertEquals(utf16, configuration.getCharSet());
        assertTrue(configuration.isDebug());
        assertSame(connection, configuration.getConnection());
        assertSame(registration, configuration.getRegistration());
        assertSame(reconnect, configuration.getReconnect());
        assertSame(raw, configuration.getEventHandlers());
        assertSame(typed, configuration.getMessageHandlers());
        assertSame(streams, configuration.getOutputStreams());
    }

    @Test
    public void clientConfigurationDefaults() {
        ClientConfiguration configuration = new ClientConfiguration();

        assertEquals(Charset.defaultCharset(), configuration.getCharSet());
        assertFalse(configuration.isDebug());
        assertTrue(configuration.getEventHandlers().isEmpty());
        assertTrue(configuration.getMessageHandlers().isEmpty());
        assertTrue(configuration.getOutputStreams().isEmpty());
    }

    @Test
    public void connectionConfigurationRoundTrips() {
        ConnectionConfiguration connection = new ConnectionConfiguration();

        connection.setHost("irc.example.org");
        connection.setPort(6697);
        connection.setSecure(false);
        connection.setKeepAlive(false);
        connection.setConnectTimeout(1234);
        connection.setTrustAllCertificates(true);

        assertEquals("irc.example.org", connection.getHost());
        assertEquals(6697, connection.getPort());
        assertFalse(connection.isSecure());
        assertFalse(connection.isKeepAlive());
        assertEquals(1234, connection.getConnectTimeout());
        assertTrue(connection.isTrustAllCertificates());
    }

    @Test
    public void registrationConfigurationRoundTrips() {
        RegistrationConfiguration registration = new RegistrationConfiguration();

        registration.setNick("bot");
        registration.setUsername("botuser");
        registration.setRealname("A Bot");
        registration.setPassword("secret");
        registration.setMaxNickAttempts(7);
        registration.setRegistrationTimeout(4321);

        assertEquals("bot", registration.getNick());
        assertEquals("botuser", registration.getUsername());
        assertEquals("A Bot", registration.getRealname());
        assertEquals("secret", registration.getPassword());
        assertEquals(7, registration.getMaxNickAttempts());
        assertEquals(4321, registration.getRegistrationTimeout());
    }

    @Test
    public void usernameAndRealnameFallBackToTheNick() {
        RegistrationConfiguration registration = new RegistrationConfiguration();
        registration.setNick("bot");

        assertEquals("bot", registration.getUsername());
        assertEquals("bot", registration.getRealname());
    }

    @Test
    public void registrationIsConfiguredOnlyWithAUsableNick() {
        RegistrationConfiguration registration = new RegistrationConfiguration();
        assertFalse("no nick means drive the handshake yourself",
                registration.isConfigured());

        registration.setNick("   ");
        assertFalse("whitespace is not a nick", registration.isConfigured());

        registration.setNick("bot");
        assertTrue(registration.isConfigured());
    }

    @Test
    public void reconnectConfigurationRoundTrips() {
        ReconnectConfiguration reconnect = new ReconnectConfiguration();

        reconnect.setEnabled(false);
        reconnect.setInitialDelay(11);
        reconnect.setMaxDelay(22);
        reconnect.setMultiplier(3.0);
        reconnect.setMaxAttempts(4);

        assertFalse(reconnect.isEnabled());
        assertEquals(11, reconnect.getInitialDelay());
        assertEquals(22, reconnect.getMaxDelay());
        assertEquals(3.0, reconnect.getMultiplier(), 0.0001);
        assertEquals(4, reconnect.getMaxAttempts());
    }

    @Test
    public void builderSetsEveryConnectionAndRegistrationField() {
        Charset utf8 = Charset.forName("UTF-8");
        OutputStream stream = new ByteArrayOutputStream();
        EventHandler<String> raw = new EventHandler<String>() {
            @Override
            public void publishEvent(Event<String> event) {
            }
        };
        EventHandler<IRCMessage> typed = new EventHandler<IRCMessage>() {
            @Override
            public void publishEvent(Event<IRCMessage> event) {
            }
        };

        ClientConfiguration configuration = new ClientConfigurationBuilder()
                .host("irc.example.org")
                .port(6697)
                .secure(true)
                .keepAlive(true)
                .connectTimeout(2500)
                .trustAllCertificates(true)
                .nick("bot")
                .username("botuser")
                .realname("A Bot")
                .password("secret")
                .capability("sasl")
                .registrationTimeout(9000)
                .reconnect(false)
                .reconnectBackoff(10, 20, 3)
                .debug(true)
                .charset(utf8)
                .outputStream(stream)
                .eventListener(raw)
                .messageListener(typed)
                .build();

        assertEquals(2500, configuration.getConnection().getConnectTimeout());
        assertTrue(configuration.getConnection().isTrustAllCertificates());
        assertEquals("botuser", configuration.getRegistration().getUsername());
        assertEquals("A Bot", configuration.getRegistration().getRealname());
        assertEquals("secret", configuration.getRegistration().getPassword());
        assertEquals(Arrays.asList("sasl"), configuration.getRegistration().getCapabilities());
        assertEquals(9000, configuration.getRegistration().getRegistrationTimeout());
        assertFalse(configuration.getReconnect().isEnabled());
        assertEquals(10, configuration.getReconnect().getInitialDelay());
        assertEquals(20, configuration.getReconnect().getMaxDelay());
        assertEquals(3, configuration.getReconnect().getMaxAttempts());
        assertTrue(configuration.isDebug());
        assertEquals(utf8, configuration.getCharSet());
        assertEquals(Arrays.asList(stream), configuration.getOutputStreams());
        assertEquals(Arrays.asList(raw), configuration.getEventHandlers());
        assertEquals(Arrays.asList(typed), configuration.getMessageHandlers());
    }

    @Test
    public void builderAcceptsAWholeConnectionObject() {
        ConnectionConfiguration connection = new ConnectionConfiguration();
        connection.setHost("irc.example.org");
        connection.setPort(6667);

        ClientConfiguration configuration = new ClientConfigurationBuilder()
                .connection(connection)
                .build();

        assertSame(connection, configuration.getConnection());
    }

    @Test(expected = NullPointerException.class)
    public void builderRejectsANullConnection() {
        new ClientConfigurationBuilder().connection(null);
    }

    @Test(expected = NullPointerException.class)
    public void builderRejectsANullEventListener() {
        new ClientConfigurationBuilder().eventListener(null);
    }

    @Test(expected = NullPointerException.class)
    public void builderRejectsANullMessageListener() {
        new ClientConfigurationBuilder().messageListener(null);
    }

    @Test(expected = NullPointerException.class)
    public void builderRejectsANullOutputStream() {
        new ClientConfigurationBuilder().outputStream(null);
    }

    @Test(expected = NullPointerException.class)
    public void builderRejectsANullCharset() {
        new ClientConfigurationBuilder().charset(null);
    }

    @Test(expected = NullPointerException.class)
    public void builderRejectsANullCapability() {
        new ClientConfigurationBuilder().capability(null);
    }

    @Test(expected = IllegalStateException.class)
    public void builderRejectsAPortAboveTheValidRange() {
        new ClientConfigurationBuilder().host("irc.example.org").port(70000).build();
    }

    @Test(expected = IllegalStateException.class)
    public void builderRejectsABlankHost() {
        new ClientConfigurationBuilder().host("   ").port(6667).build();
    }
}

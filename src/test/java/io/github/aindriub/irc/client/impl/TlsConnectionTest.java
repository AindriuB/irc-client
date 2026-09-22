package io.github.aindriub.irc.client.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.aindriub.irc.client.configuration.ClientConfigurationBuilder;
import io.github.aindriub.irc.client.testsupport.StubIRCServer;

/**
 * TLS is the default mode, so it needs exercising against a real handshake rather
 * than assumed to work.
 */
public class TlsConnectionTest {

    private static final long TIMEOUT = 5000;

    private StubIRCServer server;
    private BasicIRCClient client;

    @Before
    public void setUp() throws Exception {
        server = StubIRCServer.tls();
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            try {
                client.disconnect();
            } catch (RuntimeException e) {
                // Already down.
            }
        }
        server.close();
    }

    @Test
    public void registersOverTls() throws Exception {
        client = client(true);

        client.connect();

        assertTrue(client.isConnected());
        assertTrue(client.isRegistered());
        assertTrue("the handshake must have travelled over the encrypted connection",
                server.awaitLine("NICK bot", TIMEOUT));
    }

    @Test
    public void rejectsAnUntrustedCertificateByDefault() {
        // The stub presents a self-signed certificate. Without the opt-in this must
        // fail: an encrypted but unauthenticated connection is interceptable, and
        // the library used to accept one unconditionally.
        client = client(false);

        try {
            client.connect();
            fail("expected the self-signed certificate to be rejected");
        } catch (RuntimeException expected) {
            assertFalse("the client must not consider itself registered",
                    client.isRegistered());
        }
    }

    private BasicIRCClient client(boolean trustAll) {
        return new BasicIRCClient(new ClientConfigurationBuilder()
                .host("localhost")
                .port(server.getPort())
                .secure(true)
                .trustAllCertificates(trustAll)
                .nick("bot")
                .reconnect(false)
                .registrationTimeout(3000)
                .build());
    }
}

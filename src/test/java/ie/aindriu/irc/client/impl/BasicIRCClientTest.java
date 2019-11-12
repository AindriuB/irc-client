package ie.aindriu.irc.client.impl;

import org.junit.Before;
import org.junit.Test;

import ie.aindriu.irc.client.Connection;

public class BasicIRCClientTest {

    private Connection host;
    
    private BasicIRCClient client;
    
    @Before
    public void setUp() {
	host = new Connection();
	host.setHost("irc.chat.twitch.tv");
	host.setPort(6697);
	client = new BasicIRCClient(host);
    }
    
    @Test
    public void testConnect() throws InterruptedException {
	client.connect();
	client.sendString("oauth:test");
	client.disconnect();
    }

}

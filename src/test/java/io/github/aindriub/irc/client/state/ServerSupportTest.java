package io.github.aindriub.irc.client.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.aindriub.irc.client.message.IRCMessageParser;

public class ServerSupportTest {

    @Test
    public void usesRfcDefaultsUntilTheServerSaysOtherwise() {
        ServerSupport support = new ServerSupport();

        assertTrue("+k takes a key", support.takesParameter('k', true));
        assertTrue("-k still names the key", support.takesParameter('k', false));
        assertTrue("+l takes a limit", support.takesParameter('l', true));
        assertFalse("-l just removes it", support.takesParameter('l', false));
        assertTrue("+b takes a mask", support.takesParameter('b', true));
        assertFalse("+m takes nothing", support.takesParameter('m', true));
        assertTrue("status modes always take a nick", support.takesParameter('o', true));
        assertEquals(UserStatus.OPERATOR, support.statusFor('o'));
        assertNull(support.statusFor('m'));
    }

    @Test
    public void readsChanmodes() {
        ServerSupport support = supporting("CHANMODES=beI,k,lj,imnpstr");

        assertTrue(support.takesParameter('e', true));
        assertTrue(support.takesParameter('I', false));
        assertTrue(support.takesParameter('j', true));
        assertFalse("group C takes nothing when unset", support.takesParameter('j', false));
        assertFalse(support.takesParameter('r', true));
    }

    @Test
    public void readsPrefix() {
        ServerSupport support = supporting("PREFIX=(qaohv)~&@%+");

        assertEquals(UserStatus.OWNER, support.statusFor('q'));
        assertEquals(UserStatus.ADMIN, support.statusFor('a'));
        assertEquals(UserStatus.OPERATOR, support.statusFor('o'));
        assertEquals(UserStatus.HALF_OPERATOR, support.statusFor('h'));
        assertEquals(UserStatus.VOICE, support.statusFor('v'));
    }

    @Test
    public void aServerWithFewerStatusesDropsTheOnesItDoesNotHave() {
        ServerSupport support = supporting("PREFIX=(ov)@+");

        assertEquals(UserStatus.OPERATOR, support.statusFor('o'));
        assertNull("this server has no half operators", support.statusFor('h'));
    }

    @Test
    public void keepsValuelessTokens() {
        ServerSupport support = supporting("WHOX", "SAFELIST", "NETWORK=Example");

        assertEquals("", support.get("WHOX"));
        assertTrue(support.supports("SAFELIST"));
        assertEquals("Example", support.get("NETWORK"));
        assertEquals(3, support.getTokens().size());
    }

    @Test
    public void tokenLookupIgnoresCase() {
        ServerSupport support = supporting("NETWORK=Example");

        assertEquals("Example", support.get("network"));
        assertNull(support.get(null));
        assertFalse(support.supports("nope"));
    }

    @Test
    public void ignoresTheHumanReadableTail() {
        ServerSupport support = supporting("NETWORK=Example");

        assertFalse("'are supported by this server' is not a token",
                support.getTokens().containsKey("ARE"));
    }

    @Test
    public void ignoresMalformedDefinitionsRatherThanBreakingTheDefaults() {
        ServerSupport support = supporting("CHANMODES=only,two", "PREFIX=ohv@%+",
                "PREFIX=(ohv)@%", "PREFIX=(xyz)123");

        // Every one of those is unusable, so the RFC defaults must still stand.
        assertEquals(UserStatus.OPERATOR, support.statusFor('o'));
        assertTrue(support.takesParameter('k', true));
    }

    @Test
    public void laterLinesAddToEarlierOnes() {
        ServerSupport support = new ServerSupport();
        support.apply(IRCMessageParser.parse(":server 005 bot NETWORK=Example :are supported"));
        support.apply(IRCMessageParser.parse(":server 005 bot CHANTYPES=# :are supported"));

        assertEquals("Example", support.get("NETWORK"));
        assertEquals("#", support.get("CHANTYPES"));
    }

    @Test
    public void copesWithNoTrailingPart() {
        ServerSupport support = new ServerSupport();

        support.apply(IRCMessageParser.parse(":server 005 bot NETWORK=Example"));

        assertEquals("Example", support.get("NETWORK"));
    }

    @Test
    public void describesItself() {
        assertTrue(supporting("NETWORK=Example").toString().contains("NETWORK"));
    }

    @Test
    public void theTokenMapIsImmutable() {
        try {
            supporting("NETWORK=Example").getTokens().put("NOPE", "");
            throw new AssertionError("expected the token map to be immutable");
        } catch (UnsupportedOperationException expected) {
            // as expected
        }
    }

    private static ServerSupport supporting(String... tokens) {
        ServerSupport support = new ServerSupport();
        support.apply(IRCMessageParser.parse(":server 005 bot " + String.join(" ", tokens)
                + " :are supported by this server"));
        return support;
    }
}

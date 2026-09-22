package io.github.aindriub.irc.client.command;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class CommandTest {

    @Test
    public void joinRendersASingleChannel() {
        assertEquals("JOIN #channel", new Join("#channel").toString());
    }

    @Test
    public void joinRendersACommaSeparatedChannelList() {
        assertEquals("JOIN #one,#two,#three",
                new Join(Arrays.asList("#one", "#two", "#three")).toString());
    }

    @Test
    public void partRendersPartAndNotJoin() {
        assertEquals("PART #channel", new Part("#channel").toString());
        assertEquals("PART #one,#two", new Part(Arrays.asList("#one", "#two")).toString());
    }

    @Test
    public void nickRendersNickAndNotPart() {
        assertEquals("NICK someone", new Nick("someone").toString());
    }

    @Test
    public void whoSeparatesTheNickWithASpace() {
        assertEquals("WHO someone", new Who("someone").toString());
    }

    @Test
    public void quitRendersTheBareCommand() {
        assertEquals("QUIT", new Quit().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void joinRejectsAnEmptyChannelList() {
        new Join(Collections.<String>emptyList());
    }
}

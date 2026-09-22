package io.github.aindriub.irc.client.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import io.github.aindriub.irc.client.IRCText;

public class MessagesTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Charset LATIN_1 = Charset.forName("ISO-8859-1");

    @Test
    public void leavesAShortMessageAlone() {
        assertEquals(Collections.singletonList("hello there"),
                Messages.split("hello there", 20, UTF_8));
    }

    @Test
    public void countsBytesRatherThanCharacters() {
        // Four bytes each in UTF-8, one character each. A check against length()
        // would pass a line four times over the limit.
        String emoji = repeat("😀", 200);

        assertEquals(800, IRCText.byteLength(emoji, UTF_8));
        assertEquals(400, emoji.length());

        for (String piece : Messages.split(emoji, 20, UTF_8)) {
            assertTrue("piece is " + IRCText.byteLength(piece, UTF_8) + " bytes",
                    IRCText.byteLength(piece, UTF_8) <= IRCText.MAX_MESSAGE_BYTES - 20);
        }
    }

    @Test
    public void neverSplitsAcrossACharacter() {
        String emoji = repeat("😀", 200);

        for (String piece : Messages.split(emoji, 20, UTF_8)) {
            // A split between the halves of a surrogate pair leaves a replacement
            // character behind, which is corruption rather than truncation.
            assertFalse("piece contains a broken character: " + piece,
                    piece.contains("�"));
            assertEquals("piece should be whole emoji", 0, piece.length() % 2);
        }
        assertEquals(emoji, String.join("", Messages.split(emoji, 20, UTF_8)));
    }

    @Test
    public void theSameTextSplitsDifferentlyInADifferentCharset() {
        String accented = repeat("é", 400);

        // Two bytes each in UTF-8, one in Latin-1: the charset decides the budget.
        assertTrue(Messages.split(accented, 20, UTF_8).size()
                > Messages.split(accented, 20, LATIN_1).size());
    }

    @Test
    public void prefersToSplitOnASpace() {
        String words = repeat("word ", 200).trim();

        List<String> pieces = Messages.split(words, 20, UTF_8);

        for (String piece : pieces) {
            assertFalse("a piece should not start mid-word: " + piece.substring(0, 8),
                    piece.startsWith("ord") || piece.startsWith("rd "));
            assertTrue(piece.startsWith("word"));
        }
    }

    @Test
    public void fallsBackToTheByteBudgetWhenThereIsNoSpace() {
        // A URL, or anything else with no break in it. Walking back for a space
        // that is not there would discard the piece.
        String unbroken = repeat("a", 2000);

        List<String> pieces = Messages.split(unbroken, 20, UTF_8);

        assertTrue(pieces.size() >= 4);
        assertEquals(unbroken, String.join("", pieces));
    }

    @Test
    public void doesNotThrowAwayMostOfAPieceHuntingForASpace() {
        // A long run followed by one early space: walking back to it would waste
        // most of the budget and turn one split into several.
        String text = "ab " + repeat("c", 400);

        List<String> pieces = Messages.split(text, 20, UTF_8);

        assertTrue("expected a tight split, got " + pieces.size() + " pieces",
                pieces.size() <= 2);
    }

    @Test
    public void everyPieceOfARealMessageFits() {
        String text = repeat("the quick brown fox jumps over the lazy dog ", 40);

        for (PrivMsg message : PrivMsg.split("#channel", text, UTF_8)) {
            int bytes = IRCText.byteLength(message.render(), UTF_8) + 2;
            assertTrue(message.render().length() + " render is " + bytes + " bytes",
                    bytes <= IRCText.MAX_MESSAGE_BYTES);
        }
    }

    @Test
    public void splittingPreservesTheWords() {
        String text = repeat("alpha beta gamma delta ", 30).trim();

        // Java 8: no Stream.toList(). The library targets 8, and so does its suite.
        String rejoined = String.join(" ", PrivMsg.split("#chan", text, UTF_8).stream()
                .map(m -> m.render().substring("PRIVMSG #chan :".length()))
                .collect(Collectors.toList()));

        assertEquals(text, rejoined);
    }

    @Test
    public void anEmptyBodyStaysOneMessage() {
        assertEquals(1, Messages.split("", 20, UTF_8).size());
        assertEquals(1, PrivMsg.split("#chan", "", UTF_8).size());
    }

    @Test
    public void noticeSplitsOnTheSameTerms() {
        List<Notice> pieces = Notice.split("#chan", repeat("x ", 400).trim(), UTF_8);

        assertTrue(pieces.size() > 1);
        for (Notice piece : pieces) {
            assertTrue(IRCText.byteLength(piece.render(), UTF_8) + 2
                    <= IRCText.MAX_MESSAGE_BYTES);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesAnOverheadThatLeavesNoRoom() {
        Messages.split("anything", IRCText.MAX_MESSAGE_BYTES + 1, UTF_8);
    }

    @Test
    public void requireFitsNamesBothSizes() {
        try {
            IRCText.requireFits(repeat("x", 600), UTF_8);
            fail("expected an over-long line to be refused");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("602"));
            assertTrue(e.getMessage(), e.getMessage().contains("512"));
        }
    }

    @Test
    public void requireFitsAllowsALineExactlyAtTheLimit() {
        // 510 bytes plus CRLF is exactly 512, which is legal.
        assertEquals(510, IRCText.requireFits(repeat("x", 510), UTF_8).length());
    }

    private static String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}

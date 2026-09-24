package io.github.aindriub.irc.client.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CaseMappingTest {

    @Test
    public void allThreeFoldAsciiLetters() {
        for (CaseMapping mapping : CaseMapping.values()) {
            assertEquals("wrong for " + mapping, "abcxyz", mapping.fold("ABCxyz"));
        }
    }

    @Test
    public void rfc1459AlsoFoldsBracketsAndTilde() {
        assertEquals("{}|^", CaseMapping.RFC1459.fold("[]\\~"));
    }

    @Test
    public void strictRfc1459FoldsBracketsButLeavesTildeAlone() {
        assertEquals("{}|~", CaseMapping.STRICT_RFC1459.fold("[]\\~"));
    }

    @Test
    public void asciiLeavesBracketsAndTildeAlone() {
        assertEquals("[]\\~", CaseMapping.ASCII.fold("[]\\~"));
    }

    @Test
    public void nonAsciiIsNeverTouched() {
        for (CaseMapping mapping : CaseMapping.values()) {
            assertEquals("wrong for " + mapping, "İÄ", mapping.fold("İÄ"));
        }
    }

    @Test
    public void foldOfNullIsNull() {
        for (CaseMapping mapping : CaseMapping.values()) {
            assertEquals(null, mapping.fold(null));
        }
    }

    @Test
    public void equalsFoldsBothSides() {
        assertTrue(CaseMapping.RFC1459.equals("Nick[1]", "nick{1}"));
        assertFalse(CaseMapping.ASCII.equals("Nick[1]", "nick{1}"));
    }

    @Test
    public void equalsIsNullSafe() {
        assertTrue("two nulls are equal", CaseMapping.RFC1459.equals(null, null));
        assertFalse("one null is not", CaseMapping.RFC1459.equals(null, "nick"));
        assertFalse("one null is not", CaseMapping.RFC1459.equals("nick", null));
    }

    @Test
    public void forTokenDefaultsToRfc1459ForNull() {
        assertEquals(CaseMapping.RFC1459, CaseMapping.forToken(null));
    }

    @Test
    public void forTokenMatchesCaseInsensitively() {
        assertEquals(CaseMapping.ASCII, CaseMapping.forToken("ASCII"));
        assertEquals(CaseMapping.RFC1459, CaseMapping.forToken("Rfc1459"));
        assertEquals(CaseMapping.STRICT_RFC1459, CaseMapping.forToken("Strict-RFC1459"));
    }

    @Test
    public void forTokenFallsBackToAsciiForAnythingElse() {
        assertEquals(CaseMapping.ASCII, CaseMapping.forToken("rfc8265"));
    }
}

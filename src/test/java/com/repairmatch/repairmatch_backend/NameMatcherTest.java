package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.identity.NameMatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NameMatcherTest {

    @Test
    void ignoresAccentsCaseAndPunctuation() {
        assertEquals(true, NameMatcher.matches("Jos\u00e9 P\u00e9rez", "JOSE PEREZ"));
        assertEquals(true, NameMatcher.matches("  mar\u00eda   o'neil ", "Maria O-Neil"));
    }

    @Test
    void orderOfNamesDoesNotMatter() {
        assertEquals(true, NameMatcher.matches("Ana Quispe", "QUISPE MAMANI ANA"));
        assertEquals(true, NameMatcher.matches("Quispe Ana", "Ana Quispe"));
    }

    @Test
    void extraNamesOnTheDocumentAreAllowed() {
        assertEquals(true, NameMatcher.matches("Juan Torres", "Juan Carlos Torres Lopez"));
    }

    @Test
    void aProfileTokenMissingFromTheDocumentIsAMismatch() {
        assertEquals(false, NameMatcher.matches("Juan Torres Diaz", "Juan Torres"));
        assertEquals(false, NameMatcher.matches("Luis Ramos", "Juan Torres"));
    }

    @Test
    void aSingleTokenProfileNeverMatches() {
        assertEquals(false, NameMatcher.matches("Juan", "Juan Torres"));
        assertEquals(false, NameMatcher.matches("Juan Juan", "Juan"));
    }

    @Test
    void missingOrEmptyNamesYieldNull() {
        assertNull(NameMatcher.matches(null, "Juan Torres"));
        assertNull(NameMatcher.matches("Juan Torres", null));
        assertNull(NameMatcher.matches("", "Juan Torres"));
        assertNull(NameMatcher.matches("Juan Torres", "  "));
        assertNull(NameMatcher.matches("...", "Juan Torres"));
    }
}

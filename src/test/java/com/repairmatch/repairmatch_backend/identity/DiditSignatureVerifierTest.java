package com.repairmatch.repairmatch_backend.identity;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiditSignatureVerifierTest {

    private static final String SECRET = "whsec_test";
    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final String TS = String.valueOf(NOW.getEpochSecond());
    /** Keys out of order and a whole-valued float: V2 must see {"score":97,"session_id":"s1","status":"Approved"}. */
    private static final String BODY = "{\"status\": \"Approved\", \"session_id\": \"s1\", \"score\": 97.0}";
    private static final String CANONICAL = "{\"score\":97,\"session_id\":\"s1\",\"status\":\"Approved\"}";

    private final DiditSignatureVerifier verifier = new DiditSignatureVerifier(JsonMapper.builder().build());

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String sign(String payload, String secret) {
        return DiditSignatureVerifier.hmacHex(bytes(payload), secret);
    }

    private static DiditSignatureVerifier.Signatures sigs(String ts, String v2, String v2Prev, String raw, String rawPrev) {
        return new DiditSignatureVerifier.Signatures(ts, v2, v2Prev, raw, rawPrev);
    }

    @Test
    void canonicalizationSortsKeysAndTurnsWholeFloatsIntoIntegers() {
        assertEquals(CANONICAL, verifier.canonicalize(bytes(BODY)));
        assertEquals("{\"a\":[1,2.5,{\"b\":null,\"c\":\"ñ\\n\"}],\"z\":true}",
                verifier.canonicalize(bytes("{\"z\":true,\"a\":[1,2.5,{\"c\":\"ñ\\n\",\"b\":null}]}")));
    }

    @Test
    void acceptsAValidV2Signature() {
        assertTrue(verifier.verify(bytes(BODY), sigs(TS, sign(CANONICAL, SECRET), null, null, null), SECRET, NOW));
    }

    @Test
    void acceptsAValidRawBodySignature() {
        assertTrue(verifier.verify(bytes(BODY), sigs(TS, null, null, sign(BODY, SECRET), null), SECRET, NOW));
    }

    @Test
    void acceptsTheSignatureMadeWithThePreviousSecretAfterARotation() {
        assertTrue(verifier.verify(bytes(BODY), sigs(TS, null, sign(CANONICAL, "old-secret"), null, null), "old-secret", NOW));
        assertTrue(verifier.verify(bytes(BODY), sigs(TS, null, null, null, sign(BODY, SECRET)), SECRET, NOW));
    }

    @Test
    void rejectsAWrongSecretATamperedBodyAndMissingSignatures() {
        assertFalse(verifier.verify(bytes(BODY), sigs(TS, sign(CANONICAL, "other"), null, null, null), SECRET, NOW));
        String tampered = BODY.replace("Approved", "Declined");
        assertFalse(verifier.verify(bytes(tampered), sigs(TS, sign(CANONICAL, SECRET), null, sign(BODY, SECRET), null), SECRET, NOW));
        assertFalse(verifier.verify(bytes(BODY), sigs(TS, null, null, null, null), SECRET, NOW));
        assertFalse(verifier.verify(bytes(BODY), sigs(TS, "  ", "", null, null), SECRET, NOW));
    }

    @Test
    void rejectsStaleOrMissingOrMalformedTimestamps() {
        String valid = sign(CANONICAL, SECRET);
        String stale = String.valueOf(NOW.getEpochSecond() - 301);
        String future = String.valueOf(NOW.getEpochSecond() + 301);
        assertFalse(verifier.verify(bytes(BODY), sigs(stale, valid, null, null, null), SECRET, NOW));
        assertFalse(verifier.verify(bytes(BODY), sigs(future, valid, null, null, null), SECRET, NOW));
        assertFalse(verifier.verify(bytes(BODY), sigs(null, valid, null, null, null), SECRET, NOW));
        assertFalse(verifier.verify(bytes(BODY), sigs("abc", valid, null, null, null), SECRET, NOW));
        assertTrue(verifier.verify(bytes(BODY), sigs(String.valueOf(NOW.getEpochSecond() - 300), valid, null, null, null), SECRET, NOW));
    }

    @Test
    void rejectsEverythingWhenNoSecretIsConfigured() {
        assertFalse(verifier.verify(bytes(BODY), sigs(TS, "deadbeef", null, null, null), "", NOW));
        assertFalse(verifier.verify(bytes(BODY), sigs(TS, sign(CANONICAL, SECRET), null, null, null), null, NOW));
    }

    @Test
    void anUnparsableBodyStillVerifiesAgainstTheRawSignatureOnly() {
        String notJson = "not json";
        assertTrue(verifier.verify(bytes(notJson), sigs(TS, null, null, sign(notJson, SECRET), null), SECRET, NOW));
        assertFalse(verifier.verify(bytes(notJson), sigs(TS, sign(notJson, SECRET), null, null, null), SECRET, NOW));
    }
}

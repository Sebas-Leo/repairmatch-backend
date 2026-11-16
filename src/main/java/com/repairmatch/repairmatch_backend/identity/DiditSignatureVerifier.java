package com.repairmatch.repairmatch_backend.identity;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Verifies Didit webhook signatures (https://docs.didit.me/integration/webhooks.md).
 *
 * <p>Order: reject stale {@code X-Timestamp} (> 300 s), then accept an HMAC-SHA256 hex match, compared in
 * constant time, on {@code X-Signature-V2} (canonical JSON: sorted keys, compact separators, Unicode kept,
 * whole floats as integers) or on {@code X-Signature} (exact raw bytes), each also against its
 * {@code -Previous} counterpart sent for 24 h after a secret rotation.
 * {@code X-Signature-Simple} is deprecated and does not cover the decision, so it is never accepted.
 */
@Component
public class DiditSignatureVerifier {

    static final Duration MAX_SKEW = Duration.ofSeconds(300);

    public record Signatures(String timestamp, String v2, String v2Previous, String raw, String rawPrevious) {
    }

    private final ObjectMapper mapper;

    public DiditSignatureVerifier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public boolean verify(byte[] rawBody, Signatures headers, String secret, Instant now) {
        if (rawBody == null || secret == null || secret.isBlank() || headers.timestamp() == null) return false;
        long sentAt;
        try {
            sentAt = Long.parseLong(headers.timestamp().trim());
        } catch (NumberFormatException ex) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - sentAt) > MAX_SKEW.toSeconds()) return false;

        boolean valid = false;
        String canonical = canonicalize(rawBody);
        if (canonical != null) {
            byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
            valid |= matches(canonicalBytes, secret, headers.v2());
            valid |= matches(canonicalBytes, secret, headers.v2Previous());
        }
        valid |= matches(rawBody, secret, headers.raw());
        valid |= matches(rawBody, secret, headers.rawPrevious());
        return valid;
    }

    /** HMAC-SHA256 as lowercase hex. */
    static String hmacHex(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    private static boolean matches(byte[] payload, String secret, String header) {
        if (header == null || header.isBlank()) return false;
        byte[] expected = hmacHex(payload, secret).getBytes(StandardCharsets.UTF_8);
        byte[] received = header.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, received);
    }

    /** Same output as Python's {@code json.dumps(sort_keys=True, separators=(",", ":"), ensure_ascii=False)}. */
    String canonicalize(byte[] rawBody) {
        try {
            Object parsed = mapper.readValue(rawBody, Object.class);
            StringBuilder out = new StringBuilder(rawBody.length);
            write(parsed, out);
            return out.toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), v));
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                if (!first) out.append(',');
                first = false;
                quote(e.getKey(), out);
                out.append(':');
                write(e.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) out.append(',');
                write(list.get(i), out);
            }
            out.append(']');
        } else if (value instanceof String s) {
            quote(s, out);
        } else if (value instanceof Boolean b) {
            out.append(b ? "true" : "false");
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (!Double.isNaN(d) && !Double.isInfinite(d) && d == Math.rint(d)) {
                out.append(BigDecimal.valueOf(d).toBigInteger());
            } else {
                out.append(value);
            }
        } else {
            out.append(value);
        }
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}

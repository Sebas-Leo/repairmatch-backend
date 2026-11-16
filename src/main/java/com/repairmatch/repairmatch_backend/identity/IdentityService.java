package com.repairmatch.repairmatch_backend.identity;

import com.repairmatch.repairmatch_backend.exception.AccountUnavailableException;
import com.repairmatch.repairmatch_backend.exception.IdentityNotConfiguredException;
import com.repairmatch.repairmatch_backend.exception.InvalidStateException;
import com.repairmatch.repairmatch_backend.exception.InvalidWebhookSignatureException;
import com.repairmatch.repairmatch_backend.model.IdentityStatus;
import com.repairmatch.repairmatch_backend.model.User;
import com.repairmatch.repairmatch_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional "is a real person" layer backed by Didit. It never blocks using the app and stores only
 * the status, its timestamp and the provider session id.
 */
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final UserRepository users;
    private final DiditProperties properties;
    private final DiditClient didit;
    private final DiditSignatureVerifier signatures;
    private final ObjectMapper mapper;

    /** Not transactional on purpose: the remote call must not hold a database connection. */
    public IdentityDtos.SessionResponse createSession(UUID userId) {
        User user = users.findById(userId).orElseThrow(AccountUnavailableException::new);
        if (user.isIdentityVerified()) {
            throw new InvalidStateException("Tu identidad ya está verificada");
        }
        if (!properties.canCreateSessions()) {
            throw new IdentityNotConfiguredException("La verificación de identidad no está disponible por ahora");
        }
        DiditClient.CreatedSession session = didit.createSession(userId.toString());
        user.setIdentityStatus(IdentityStatus.PENDING);
        user.setIdentitySessionId(session.sessionId());
        users.save(user);
        return new IdentityDtos.SessionResponse(session.url(), IdentityStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public IdentityDtos.StatusResponse status(UUID userId) {
        User user = users.findById(userId).orElseThrow(AccountUnavailableException::new);
        return new IdentityDtos.StatusResponse(user.getIdentityStatus(), user.getIdentityVerifiedAt(),
                user.getIdentityNameMatches());
    }

    /** Maps a Didit session status (case-sensitive per the docs) to ours; empty means "ignore the event". */
    static Optional<IdentityStatus> map(String diditStatus) {
        if (diditStatus == null) return Optional.empty();
        return switch (diditStatus) {
            case "Approved" -> Optional.of(IdentityStatus.VERIFIED);
            case "Declined" -> Optional.of(IdentityStatus.REJECTED);
            case "Not Started", "In Progress", "In Review", "Resubmitted", "Awaiting User" ->
                    Optional.of(IdentityStatus.PENDING);
            // The user never finished (or an old approval lapsed): they can simply start again.
            case "Expired", "Abandoned", "Kyc Expired" -> Optional.of(IdentityStatus.NOT_STARTED);
            default -> Optional.empty();
        };
    }

    /**
     * Applies a signed Didit webhook. Idempotent: replays and out-of-order deliveries leave the
     * same state, and events for sessions that are not the user's current one are ignored.
     */
    @Transactional
    public void handleWebhook(byte[] rawBody, DiditSignatureVerifier.Signatures headers, Instant now) {
        if (!properties.canVerifyWebhooks()) {
            throw new IdentityNotConfiguredException("La verificación de identidad no está disponible por ahora");
        }
        if (!signatures.verify(rawBody, headers, properties.getWebhookSecret(), now)) {
            throw new InvalidWebhookSignatureException();
        }
        Map<?, ?> payload;
        try {
            payload = mapper.readValue(rawBody, Map.class);
        } catch (RuntimeException ex) {
            return; // signed but not an object: nothing to apply, and a retry would not help
        }
        if (!(payload.get("session_id") instanceof String sessionId)
                || !(payload.get("status") instanceof String diditStatus)) {
            return;
        }
        Object type = payload.get("webhook_type");
        if (type != null && !"status.updated".equals(type)) return;

        Optional<IdentityStatus> mapped = map(diditStatus);
        if (mapped.isEmpty()) return;
        findUser(payload.get("vendor_data"), sessionId).ifPresent(user -> apply(user, sessionId, mapped.get(),
                mapped.get() == IdentityStatus.VERIFIED ? nameMatch(user, payload.get("decision")) : null));
    }

    private Optional<User> findUser(Object vendorData, String sessionId) {
        if (vendorData instanceof String vendor) {
            try {
                Optional<User> byVendor = users.findById(UUID.fromString(vendor));
                if (byVendor.isPresent()) return byVendor;
            } catch (IllegalArgumentException ignored) {
                // fall through to the session id
            }
        }
        return users.findByIdentitySessionId(sessionId);
    }

    /**
     * Compares the profile name with the document name from {@code decision.id_verifications[]} (Didit v3).
     * The document name lives only in local variables: it is neither stored nor logged. Null when absent.
     */
    private static Boolean nameMatch(User user, Object decision) {
        if (!(decision instanceof Map<?, ?> d) || !(d.get("id_verifications") instanceof List<?> items)) return null;
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> doc)) continue;
            String documentName = documentName(doc);
            if (documentName != null) return NameMatcher.matches(user.getName(), documentName);
        }
        return null;
    }

    private static String documentName(Map<?, ?> doc) {
        if (doc.get("full_name") instanceof String full && !full.isBlank()) return full;
        String first = doc.get("first_name") instanceof String f ? f.trim() : "";
        String last = doc.get("last_name") instanceof String l ? l.trim() : "";
        String joined = (first + " " + last).trim();
        return joined.isEmpty() ? null : joined;
    }

    private void apply(User user, String sessionId, IdentityStatus next, Boolean nameMatches) {
        if (!sessionId.equals(user.getIdentitySessionId())) return; // stale session
        IdentityStatus current = user.getIdentityStatus();
        if (current == next) {
            // Replays may backfill a flag that was missing (e.g. approved before this field existed).
            if (next == IdentityStatus.VERIFIED && user.getIdentityNameMatches() == null && nameMatches != null) {
                user.setIdentityNameMatches(nameMatches);
                users.save(user);
            }
            return;
        }
        // A late "In Progress" must not undo an approval; only a decline or an expiry can.
        if (current == IdentityStatus.VERIFIED && next == IdentityStatus.PENDING) return;
        user.setIdentityStatus(next);
        user.setIdentityVerifiedAt(next == IdentityStatus.VERIFIED ? LocalDateTime.now() : null);
        user.setIdentityNameMatches(next == IdentityStatus.VERIFIED ? nameMatches : null);
        users.save(user);
    }
}

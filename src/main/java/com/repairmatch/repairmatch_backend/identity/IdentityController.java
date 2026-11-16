package com.repairmatch.repairmatch_backend.identity;

import com.repairmatch.repairmatch_backend.security.AccountAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/verification/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService identity;
    private final AccountAccess access;

    @PostMapping("/session")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentityDtos.SessionResponse createSession() {
        return identity.createSession(access.requireIdentity());
    }

    @GetMapping({"", "/status"})
    public IdentityDtos.StatusResponse status() {
        return identity.status(access.requireIdentity());
    }

    /** Public, called by Didit. Authenticity comes only from the HMAC signature over the raw body. */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature-V2", required = false) String v2,
            @RequestHeader(value = "X-Signature-V2-Previous", required = false) String v2Previous,
            @RequestHeader(value = "X-Signature", required = false) String raw,
            @RequestHeader(value = "X-Signature-Previous", required = false) String rawPrevious) {
        identity.handleWebhook(rawBody,
                new DiditSignatureVerifier.Signatures(timestamp, v2, v2Previous, raw, rawPrevious), Instant.now());
        return ResponseEntity.ok().build();
    }
}

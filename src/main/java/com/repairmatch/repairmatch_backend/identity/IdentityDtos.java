package com.repairmatch.repairmatch_backend.identity;

import com.repairmatch.repairmatch_backend.model.IdentityStatus;

import java.time.LocalDateTime;

public final class IdentityDtos {
    private IdentityDtos() {
    }

    /** Hosted verification link the client must open, and the user's status right after creating it. */
    public record SessionResponse(String url, IdentityStatus status) {
    }

    public record StatusResponse(IdentityStatus status, LocalDateTime verifiedAt, Boolean nameMatches) {
    }
}

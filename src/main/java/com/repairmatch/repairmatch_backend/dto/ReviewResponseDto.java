package com.repairmatch.repairmatch_backend.dto;

import com.repairmatch.repairmatch_backend.model.ReviewEntity;
import java.time.LocalDateTime;

/** A service review. It deliberately exposes no reviewer identity. */
public record ReviewResponseDto(Long id, Long serviceId, Integer rating, String comment, LocalDateTime createdAt) {
    public static ReviewResponseDto from(ReviewEntity review) {
        return new ReviewResponseDto(review.getId(), review.getService().getId(), review.getRating(),
                review.getComment(), review.getCreatedAt());
    }
}

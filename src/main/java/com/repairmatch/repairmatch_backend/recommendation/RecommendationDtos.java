package com.repairmatch.repairmatch_backend.recommendation;

import com.repairmatch.repairmatch_backend.dto.MatchingRequestResponseDto;
import com.repairmatch.repairmatch_backend.dto.ProposalResponseDto;
import com.repairmatch.repairmatch_backend.recommendation.Ranking.Factor;
import com.repairmatch.repairmatch_backend.recommendation.Ranking.Reason;

import java.time.LocalDateTime;
import java.util.List;

public final class RecommendationDtos {
    private RecommendationDtos() {
    }

    /** A proposal in "Recomendado" order; {@code recommended} marks the first one. */
    public record RecommendedProposal(
            ProposalResponseDto proposal,
            double score,
            boolean recommended,
            Double distanceKm,
            double averageRating,
            long reviewCount,
            List<Factor> factors,
            List<Reason> reasons) {
    }

    /** An open request in "Para ti" order; {@code symptom} is the local AI's reading of its description. */
    public record RecommendedRequest(
            MatchingRequestResponseDto request,
            LocalDateTime createdAt,
            long proposalCount,
            String symptom,
            double score,
            List<Factor> factors,
            List<Reason> reasons) {
    }
}

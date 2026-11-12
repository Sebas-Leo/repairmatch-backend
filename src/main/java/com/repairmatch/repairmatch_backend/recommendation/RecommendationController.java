package com.repairmatch.repairmatch_backend.recommendation;

import com.repairmatch.repairmatch_backend.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "Recomendado" for clients and "Para ti" for technicians: transparent rankings with their reasons. */
@RestController
@RequiredArgsConstructor
public class RecommendationController {
    private final RecommendationService recommendations;

    @GetMapping("/api/requests/{requestId}/proposals/recommended")
    public List<RecommendationDtos.RecommendedProposal> recommendedProposals(@PathVariable Long requestId) {
        return recommendations.recommendProposals(requestId);
    }

    @GetMapping("/api/technicians/me/recommended-requests")
    public PageResponse<RecommendationDtos.RecommendedRequest> recommendedRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return recommendations.recommendRequests(PageResponse.request(page, size, Sort.unsorted()));
    }
}

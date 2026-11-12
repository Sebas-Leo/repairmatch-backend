package com.repairmatch.repairmatch_backend.recommendation;

import com.repairmatch.repairmatch_backend.ai.Symptom;
import com.repairmatch.repairmatch_backend.recommendation.Ranking.Factor;
import com.repairmatch.repairmatch_backend.recommendation.Ranking.Reason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The rankings are pure functions: same inputs, same order, and the reasons say why. */
class RankerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 10, 0);

    private static ProposalRanker.Candidate proposal(long id, String cost, int hoursUntil, Double km,
                                                     double rating, long reviews, Double ownRating) {
        return new ProposalRanker.Candidate(id, new BigDecimal(cost), NOW.plusHours(hoursUntil), km, rating, reviews, ownRating);
    }

    private static List<String> codes(List<Reason> reasons) {
        return reasons.stream().map(Reason::code).toList();
    }

    @Test
    void aWellReviewedTechnicianBeatsASlightlyCheaperUnknownOne() {
        var ranked = ProposalRanker.rank(List.of(
                proposal(1, "45.00", 24, 3.0, 0, 0, null),
                proposal(2, "50.00", 24, 3.0, 4.9, 20, null)), NOW);

        assertThat(ranked).extracting(r -> r.candidate().proposalId()).containsExactly(2L, 1L);
        assertThat(codes(ranked.get(0).reasons())).containsExactly("TOP_RATED");
        assertThat(codes(ranked.get(1).reasons())).containsExactly("CHEAPEST", "NO_REVIEWS");
        // Same availability and distance: a tie gives nobody the "sooner" or "closer" reason.
        assertThat(ranked).allSatisfy(r -> assertThat(codes(r.reasons())).doesNotContain("SOONEST", "CLOSEST"));
    }

    @Test
    void fewReviewsStayNearThePriorAndManyReviewsSpeakForThemselves() {
        double one = ProposalRanker.reputation(proposal(1, "1", 1, null, 5.0, 1, null));
        double many = ProposalRanker.reputation(proposal(2, "1", 1, null, 4.8, 40, null));
        assertThat(one).isCloseTo(3.875, within(0.001));
        assertThat(many).isGreaterThan(one);
    }

    @Test
    void theClientsOwnGoodExperienceCountsAndIsShown() {
        var ranked = ProposalRanker.rank(List.of(
                proposal(1, "40.00", 24, 2.0, 4.0, 5, null),
                proposal(2, "40.00", 24, 2.0, 4.0, 5, 5.0)), NOW);

        assertThat(ranked.get(0).candidate().proposalId()).isEqualTo(2L);
        assertThat(ranked.get(0).reasons()).first().satisfies(reason -> {
            assertThat(reason.code()).isEqualTo("WORKED_WITH_YOU");
            assertThat(reason.label()).isEqualTo("Ya te atendió y lo calificaste con 5★");
        });
    }

    @Test
    void theScoreIsExactlyTheWeightedSumOfItsFactors() {
        var ranked = ProposalRanker.rank(List.of(
                proposal(1, "60.00", 2, 1.0, 4.5, 8, null),
                proposal(2, "30.00", 72, 12.0, 3.0, 2, null)), NOW);
        for (var r : ranked) {
            double sum = r.factors().stream().mapToDouble(Factor::contribution).sum();
            assertThat(r.score()).isCloseTo(sum, within(0.011));
            assertThat(r.factors()).extracting(Factor::weight).containsExactly(0.35, 0.25, 0.20, 0.20);
        }
        var soonest = ranked.stream().filter(r -> r.candidate().proposalId() == 1L).findFirst().orElseThrow();
        assertThat(codes(soonest.reasons())).contains("SOONEST", "CLOSEST", "TOP_RATED");
        assertThat(soonest.reasons()).filteredOn(r -> r.code().equals("SOONEST"))
                .extracting(Reason::label).containsExactly("Disponible antes: en 2 h");
    }

    @Test
    void aSingleProposalHasNoComparativeReasons() {
        var ranked = ProposalRanker.rank(List.of(proposal(1, "45.00", 24, 3.0, 4.0, 3, null)), NOW);
        assertThat(codes(ranked.get(0).reasons())).isEmpty();
        assertThat(ProposalRanker.rank(List.of(), NOW)).isEmpty();
    }

    private static RequestRanker.Candidate request(long id, String brand, Symptom symptom, double km, int hoursOld, long proposals) {
        return new RequestRanker.Candidate(id, 7L, brand, symptom, km, NOW.minusHours(hoursOld), proposals);
    }

    @Test
    void theTechniciansHistoryPullsSimilarJobsUp() {
        var history = new RequestRanker.History(Map.of(7L, 4), Map.of("samsung", 3), Map.of(Symptom.NO_ENFRIA, 2), 4);
        var ranked = RequestRanker.rank(List.of(
                request(1, "LG", Symptom.RUIDO, 2.0, 3, 0),
                request(2, "Samsung", Symptom.NO_ENFRIA, 2.0, 3, 0)), history, NOW);

        assertThat(ranked).extracting(r -> r.candidate().requestId()).containsExactly(2L, 1L);
        assertThat(ranked.get(0).reasons()).extracting(Reason::label).containsExactly(
                "A 2.0 km de ti",
                "Ya resolviste 2 casos de \"No enfría\"",
                "Ya reparaste 3 equipos Samsung");
        assertThat(RequestRanker.experience(ranked.get(0).candidate(), history)).isCloseTo(0.5 + 0.25 * 0.75 + 0.25 * 0.5, within(1e-9));
    }

    @Test
    void withoutHistoryCloseFreshAndUncontestedJobsComeFirst() {
        var ranked = RequestRanker.rank(List.of(
                request(1, "LG", null, 15.0, 3, 0),
                request(2, "LG", null, 1.0, 3, 0),
                request(3, "LG", null, 1.0, 3, 4)), RequestRanker.History.empty(), NOW);

        assertThat(ranked).extracting(r -> r.candidate().requestId()).containsExactly(2L, 3L, 1L);
        assertThat(codes(ranked.get(0).reasons())).containsExactly("NEAR", "NO_COMPETITION", "FRESH");
        assertThat(codes(ranked.get(2).reasons())).containsExactly("NO_COMPETITION", "FRESH");
        assertThat(ranked.get(0).factors()).filteredOn(f -> f.key().equals("experience"))
                .extracting(Factor::value).containsExactly(0.0);
    }
}

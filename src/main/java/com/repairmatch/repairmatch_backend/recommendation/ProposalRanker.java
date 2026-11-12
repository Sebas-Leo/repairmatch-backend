package com.repairmatch.repairmatch_backend.recommendation;

import com.repairmatch.repairmatch_backend.recommendation.Ranking.Factor;
import com.repairmatch.repairmatch_backend.recommendation.Ranking.Reason;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * "Recomendado" order of the proposals of one request. Pure function of its
 * inputs, so it is tested without Spring or a database.
 *
 * <pre>
 * score = 0.35 reputation + 0.25 price + 0.20 availability + 0.20 distance
 * reputation   = Bayesian mean of the ratings (prior 3.5 worth 3 reviews), the
 *                client's own past rating of that technician counting twice, scaled 1..5 -> 0..1
 * price        = cheapest diagnostic cost / this cost
 * availability = 1 / (1 + hours until available / 24)
 * distance     = 1 / (1 + km / 5); unknown location -> 0.5
 * </pre>
 */
public final class ProposalRanker {
    static final double W_REPUTATION = 0.35;
    static final double W_PRICE = 0.25;
    static final double W_AVAILABILITY = 0.20;
    static final double W_DISTANCE = 0.20;
    static final double PRIOR_RATING = 3.5;
    static final double PRIOR_WEIGHT = 3;
    static final double OWN_RATING_WEIGHT = 2;

    public record Candidate(
            long proposalId,
            BigDecimal diagnosticCost,
            LocalDateTime availableAt,
            Double distanceKm,
            double averageRating,
            long reviewCount,
            Double ownRating) {
    }

    public record Ranked(Candidate candidate, double score, List<Factor> factors, List<Reason> reasons) {
    }

    private ProposalRanker() {
    }

    public static List<Ranked> rank(List<Candidate> candidates, LocalDateTime now) {
        if (candidates.isEmpty()) return List.of();
        BigDecimal cheapest = candidates.stream().map(Candidate::diagnosticCost).min(Comparator.naturalOrder()).orElseThrow();

        List<Ranked> scored = new ArrayList<>();
        for (Candidate c : candidates) {
            List<Factor> factors = List.of(
                    Ranking.factor("reputation", W_REPUTATION, (reputation(c) - 1) / 4),
                    Ranking.factor("price", W_PRICE, price(cheapest, c.diagnosticCost())),
                    Ranking.factor("availability", W_AVAILABILITY, Ranking.decay(hoursUntil(now, c.availableAt()), 24)),
                    Ranking.factor("distance", W_DISTANCE, c.distanceKm() == null ? 0.5 : Ranking.decay(c.distanceKm(), 5)));
            scored.add(new Ranked(c, Ranking.score(factors), factors, List.of()));
        }

        List<Ranked> ranked = new ArrayList<>();
        for (Ranked r : scored) ranked.add(new Ranked(r.candidate(), r.score(), r.factors(), reasons(r, scored, now)));
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparing(r -> r.candidate().diagnosticCost())
                .thenComparingLong(r -> r.candidate().proposalId()));
        return List.copyOf(ranked);
    }

    /** Bayesian mean: few reviews stay near the prior, many reviews speak for themselves. */
    static double reputation(Candidate c) {
        double sum = c.averageRating() * c.reviewCount() + PRIOR_RATING * PRIOR_WEIGHT;
        double weight = c.reviewCount() + PRIOR_WEIGHT;
        if (c.ownRating() != null) {
            sum += c.ownRating() * OWN_RATING_WEIGHT;
            weight += OWN_RATING_WEIGHT;
        }
        return sum / weight;
    }

    private static double price(BigDecimal cheapest, BigDecimal cost) {
        if (cost.signum() <= 0) return 1;
        return cheapest.doubleValue() / cost.doubleValue();
    }

    private static double hoursUntil(LocalDateTime now, LocalDateTime availableAt) {
        return Duration.between(now, availableAt).toMinutes() / 60.0;
    }

    private static List<Reason> reasons(Ranked r, List<Ranked> all, LocalDateTime now) {
        Candidate c = r.candidate();
        List<Reason> reasons = new ArrayList<>();
        boolean compared = all.size() > 1;
        if (c.ownRating() != null) {
            reasons.add(new Reason("WORKED_WITH_YOU",
                    String.format(Locale.ROOT, "Ya te atendió y lo calificaste con %.0f★", c.ownRating())));
        }
        if (compared && isBest(r, all, "reputation") && c.reviewCount() > 0) {
            reasons.add(new Reason("TOP_RATED", String.format(Locale.ROOT, "Mejor reputación: %.1f★ en %d %s",
                    c.averageRating(), c.reviewCount(), c.reviewCount() == 1 ? "reseña" : "reseñas")));
        }
        if (compared && all.stream().filter(o -> o != r).allMatch(o -> o.candidate().diagnosticCost().compareTo(c.diagnosticCost()) > 0)) {
            reasons.add(new Reason("CHEAPEST", String.format(Locale.ROOT, "Precio más bajo: S/ %.2f", c.diagnosticCost())));
        }
        if (compared && isBest(r, all, "availability")) {
            reasons.add(new Reason("SOONEST", "Disponible antes: " + when(now, c.availableAt())));
        }
        if (compared && c.distanceKm() != null && isBest(r, all, "distance")) {
            reasons.add(new Reason("CLOSEST", String.format(Locale.ROOT, "El más cercano: a %.1f km", c.distanceKm())));
        }
        if (c.reviewCount() == 0) reasons.add(new Reason("NO_REVIEWS", "Todavía no tiene reseñas"));
        return List.copyOf(reasons);
    }

    /** Strictly best: on a tie nobody gets the reason, since it would not tell them apart. */
    private static boolean isBest(Ranked r, List<Ranked> all, String key) {
        double mine = value(r, key);
        return all.stream().filter(other -> other != r).allMatch(other -> value(other, key) < mine);
    }

    private static double value(Ranked r, String key) {
        return r.factors().stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow().value();
    }

    private static String when(LocalDateTime now, LocalDateTime at) {
        long hours = Duration.between(now, at).toHours();
        if (hours < 1) return "ya mismo";
        if (hours < 24) return "en " + hours + " h";
        long days = Math.round(hours / 24.0);
        return days == 1 ? "mañana" : "en " + days + " días";
    }
}

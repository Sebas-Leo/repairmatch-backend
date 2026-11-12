package com.repairmatch.repairmatch_backend.recommendation;

import com.repairmatch.repairmatch_backend.ai.Symptom;
import com.repairmatch.repairmatch_backend.recommendation.Ranking.Factor;
import com.repairmatch.repairmatch_backend.recommendation.Ranking.Reason;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Para ti" order of the open requests a technician can serve, fed by the
 * jobs that technician already won. Pure function of its inputs.
 *
 * <pre>
 * score = 0.30 distance + 0.30 experience + 0.20 freshness + 0.20 competition
 * distance    = 1 / (1 + km / 5)
 * experience  = 0.5 share of past jobs of this appliance type
 *             + 0.25 share with this brand + 0.25 share with this symptom
 *               (the symptom of every description comes from the local AI)
 * freshness   = 1 / (1 + hours since published / 48)
 * competition = 1 / (1 + proposals already received)
 * </pre>
 */
public final class RequestRanker {
    static final double W_DISTANCE = 0.30;
    static final double W_EXPERIENCE = 0.30;
    static final double W_FRESHNESS = 0.20;
    static final double W_COMPETITION = 0.20;
    static final int MAX_REASONS = 3;

    /** What the technician has done: counts of past won jobs per type, brand (lower case) and symptom. */
    public record History(Map<Long, Integer> byType, Map<String, Integer> byBrand, Map<Symptom, Integer> bySymptom, int jobs) {
        public static History empty() {
            return new History(Map.of(), Map.of(), Map.of(), 0);
        }
    }

    public record Candidate(
            long requestId,
            Long applianceTypeId,
            String brand,
            Symptom symptom,
            double distanceKm,
            LocalDateTime createdAt,
            long proposalCount) {
    }

    public record Ranked(Candidate candidate, double score, List<Factor> factors, List<Reason> reasons) {
    }

    private RequestRanker() {
    }

    public static List<Ranked> rank(List<Candidate> candidates, History history, LocalDateTime now) {
        List<Ranked> ranked = new ArrayList<>();
        for (Candidate c : candidates) {
            double hoursOld = Duration.between(c.createdAt(), now).toMinutes() / 60.0;
            List<Factor> factors = List.of(
                    Ranking.factor("distance", W_DISTANCE, Ranking.decay(c.distanceKm(), 5)),
                    Ranking.factor("experience", W_EXPERIENCE, experience(c, history)),
                    Ranking.factor("freshness", W_FRESHNESS, Ranking.decay(hoursOld, 48)),
                    Ranking.factor("competition", W_COMPETITION, Ranking.decay(c.proposalCount(), 1)));
            ranked.add(new Ranked(c, Ranking.score(factors), factors, reasons(c, history, hoursOld)));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparingDouble(r -> r.candidate().distanceKm())
                .thenComparingLong(r -> r.candidate().requestId()));
        return List.copyOf(ranked);
    }

    static double experience(Candidate c, History h) {
        if (h.jobs() == 0) return 0;
        double type = share(h.byType().get(c.applianceTypeId()), h.jobs());
        double brand = share(h.byBrand().get(brandKey(c.brand())), h.jobs());
        double symptom = c.symptom() == null ? 0 : share(h.bySymptom().get(c.symptom()), h.jobs());
        return 0.5 * type + 0.25 * brand + 0.25 * symptom;
    }

    public static String brandKey(String brand) {
        return brand == null ? "" : brand.strip().toLowerCase(Locale.ROOT);
    }

    private static double share(Integer count, int total) {
        return count == null ? 0 : count / (double) total;
    }

    private static List<Reason> reasons(Candidate c, History h, double hoursOld) {
        List<Reason> reasons = new ArrayList<>();
        if (c.distanceKm() <= 5) {
            reasons.add(new Reason("NEAR", String.format(Locale.ROOT, "A %.1f km de ti", c.distanceKm())));
        }
        Integer symptomJobs = c.symptom() == null ? null : h.bySymptom().get(c.symptom());
        if (symptomJobs != null) {
            reasons.add(new Reason("SYMPTOM_EXPERIENCE", "Ya resolviste " + cases(symptomJobs) + " de \"" + c.symptom().label() + "\""));
        }
        Integer brandJobs = h.byBrand().get(brandKey(c.brand()));
        if (brandJobs != null && !brandKey(c.brand()).isEmpty()) {
            reasons.add(new Reason("BRAND_EXPERIENCE", "Ya reparaste " + jobs(brandJobs) + " " + c.brand().strip()));
        }
        if (c.proposalCount() == 0) reasons.add(new Reason("NO_COMPETITION", "Aún sin propuestas: puedes ser el primero"));
        if (hoursOld < 24) reasons.add(new Reason("FRESH", hoursOld < 1 ? "Publicada hace minutos" : "Publicada hace " + (long) hoursOld + " h"));
        return List.copyOf(reasons.subList(0, Math.min(MAX_REASONS, reasons.size())));
    }

    private static String cases(int n) {
        return n == 1 ? "1 caso" : n + " casos";
    }

    private static String jobs(int n) {
        return n == 1 ? "1 equipo" : n + " equipos";
    }
}

package com.repairmatch.repairmatch_backend.recommendation;

import java.util.List;

/**
 * Shared vocabulary of the transparent rankings: every score is a weighted sum
 * of named factors in [0, 1], and every factor that stands out becomes a
 * human-readable reason. Nothing is hidden: the client sees the same numbers.
 */
public final class Ranking {
    private Ranking() {
    }

    /** One term of the score: {@code weight * value}, with value in [0, 1]. */
    public record Factor(String key, double weight, double value) {
        public double contribution() {
            return weight * value;
        }
    }

    /** Why an item ranks where it does: a stable code for the UI and a Spanish label. */
    public record Reason(String code, String label) {
    }

    public static double score(List<Factor> factors) {
        return round(factors.stream().mapToDouble(Factor::contribution).sum());
    }

    /** 1 at zero, 0.5 at {@code half}, approaching 0 as the quantity grows. */
    static double decay(double quantity, double half) {
        return 1.0 / (1.0 + Math.max(0, quantity) / half);
    }

    static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    static Factor factor(String key, double weight, double value) {
        return new Factor(key, weight, round(Math.max(0, Math.min(1, value))));
    }
}

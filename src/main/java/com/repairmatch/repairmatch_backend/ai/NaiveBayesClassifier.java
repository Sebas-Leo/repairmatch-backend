package com.repairmatch.repairmatch_backend.ai;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Multinomial Naive Bayes text classifier with Laplace smoothing.
 *
 * It learns, per label, how often each feature appears in the training
 * examples, and predicts the label with the highest posterior probability
 * P(label | features), returned with that probability so callers can ignore
 * low-confidence guesses. Runs in memory: no network, no external service.
 */
public final class NaiveBayesClassifier<L> {
    public record Example<L>(String text, L label) {
    }

    public record Prediction<L>(L label, double probability) {
    }

    private final Map<L, Integer> documentsPerLabel = new HashMap<>();
    private final Map<L, Map<String, Integer>> featureCounts = new HashMap<>();
    private final Map<L, Integer> featureTotals = new HashMap<>();
    private final Set<String> vocabulary = new HashSet<>();
    private int documents;

    public NaiveBayesClassifier(List<Example<L>> examples) {
        if (examples.isEmpty()) throw new IllegalArgumentException("At least one training example is required");
        for (Example<L> example : examples) train(example);
    }

    private void train(Example<L> example) {
        documents++;
        documentsPerLabel.merge(example.label(), 1, Integer::sum);
        Map<String, Integer> counts = featureCounts.computeIfAbsent(example.label(), l -> new HashMap<>());
        for (String feature : TextNormalizer.features(example.text())) {
            counts.merge(feature, 1, Integer::sum);
            featureTotals.merge(example.label(), 1, Integer::sum);
            vocabulary.add(feature);
        }
    }

    /**
     * The most likely label, or empty when the text shares no feature with the
     * training data (nothing to base a guess on).
     */
    public Optional<Prediction<L>> predict(String text) {
        List<String> features = TextNormalizer.features(text).stream().filter(vocabulary::contains).toList();
        if (features.isEmpty()) return Optional.empty();

        Map<L, Double> logScores = new HashMap<>();
        for (L label : documentsPerLabel.keySet()) {
            double score = Math.log(documentsPerLabel.get(label) / (double) documents);
            Map<String, Integer> counts = featureCounts.get(label);
            double denominator = featureTotals.getOrDefault(label, 0) + vocabulary.size();
            for (String feature : features) {
                score += Math.log((counts.getOrDefault(feature, 0) + 1) / denominator);
            }
            logScores.put(label, score);
        }
        // Softmax over log scores (shifted by the max for numerical stability).
        double max = logScores.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        double sum = logScores.values().stream().mapToDouble(s -> Math.exp(s - max)).sum();
        return logScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(best -> new Prediction<>(best.getKey(), Math.exp(best.getValue() - max) / sum));
    }
}

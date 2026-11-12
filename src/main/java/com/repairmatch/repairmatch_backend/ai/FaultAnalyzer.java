package com.repairmatch.repairmatch_backend.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local AI that structures a fault description: appliance kind and symptom
 * (Naive Bayes trained on {@code ai/training-es.tsv}), brand (dictionary with
 * typo tolerance) and model (pattern). Everything runs in process, at no cost.
 * It never diagnoses: it only organizes what the client wrote.
 */
@Component
public class FaultAnalyzer {
    /** Below this posterior probability a guess is not shown as a fact. */
    public static final double MIN_CONFIDENCE = 0.6;

    static final List<String> BRANDS = List.of(
            "Samsung", "LG", "Mabe", "Whirlpool", "Electrolux", "Bosch", "Indurama", "Panasonic",
            "Daewoo", "Sole", "Frigidaire", "Haier", "Midea", "Oster", "Coldex", "General Electric");

    // Letters and digits mixed, 3-20 chars: RT38K5930SL, WM22VV2S6R, GT32. Times like 9:00 never match.
    private static final Pattern MODEL = Pattern.compile("\\b(?=[A-Za-z0-9-]*\\d)(?=[A-Za-z0-9-]*[A-Za-z])[A-Za-z0-9-]{3,20}\\b");

    public record Guess<T>(T value, double confidence) {
    }

    public record FaultAnalysis(
            Optional<Guess<ApplianceKind>> appliance,
            Optional<Guess<Symptom>> symptom,
            Optional<String> brand,
            Optional<String> model) {
    }

    private final NaiveBayesClassifier<ApplianceKind> applianceClassifier;
    private final NaiveBayesClassifier<Symptom> symptomClassifier;

    public FaultAnalyzer() {
        List<NaiveBayesClassifier.Example<ApplianceKind>> appliances = new ArrayList<>();
        List<NaiveBayesClassifier.Example<Symptom>> symptoms = new ArrayList<>();
        for (String[] row : readTrainingData()) {
            switch (row[0]) {
                case "appliance" -> appliances.add(new NaiveBayesClassifier.Example<>(row[2], ApplianceKind.valueOf(row[1])));
                case "symptom" -> symptoms.add(new NaiveBayesClassifier.Example<>(row[2], Symptom.valueOf(row[1])));
                default -> throw new IllegalStateException("Unknown training task: " + row[0]);
            }
        }
        this.applianceClassifier = new NaiveBayesClassifier<>(appliances);
        this.symptomClassifier = new NaiveBayesClassifier<>(symptoms);
    }

    public FaultAnalysis analyze(String description) {
        String text = description == null ? "" : description;
        Optional<String> brand = detectBrand(text);
        return new FaultAnalysis(
                classifyAppliance(text),
                classifySymptom(text),
                brand,
                detectModel(text, brand.orElse(null)));
    }

    public Optional<Guess<ApplianceKind>> classifyAppliance(String text) {
        return applianceClassifier.predict(text).map(p -> new Guess<>(p.label(), p.probability()));
    }

    public Optional<Guess<Symptom>> classifySymptom(String text) {
        return symptomClassifier.predict(text).map(p -> new Guess<>(p.label(), p.probability()));
    }

    /** A known brand, tolerating one typo in names of 5+ letters ("Samsumg" -> Samsung). */
    public Optional<String> detectBrand(String text) {
        List<String> words = TextNormalizer.words(text);
        String folded = " " + String.join(" ", words) + " ";
        for (String brand : BRANDS) {
            String key = TextNormalizer.fold(brand);
            if (folded.contains(" " + key + " ")) return Optional.of(brand);
        }
        for (String word : words) {
            if (word.length() < 5) continue;
            for (String brand : BRANDS) {
                String key = TextNormalizer.fold(brand);
                if (key.length() >= 5 && !key.contains(" ") && levenshtein(word, key) <= 1) return Optional.of(brand);
            }
        }
        return Optional.empty();
    }

    Optional<String> detectModel(String text, String brand) {
        Matcher matcher = MODEL.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (brand != null && candidate.equalsIgnoreCase(brand)) continue;
            return Optional.of(candidate.toUpperCase(Locale.ROOT));
        }
        return Optional.empty();
    }

    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static List<String[]> readTrainingData() {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("ai/training-es.tsv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\t", 3);
                if (parts.length != 3) throw new IllegalStateException("Malformed training line: " + line);
                rows.add(parts);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read ai/training-es.tsv", e);
        }
        return rows;
    }
}

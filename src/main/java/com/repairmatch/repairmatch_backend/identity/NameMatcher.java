package com.repairmatch.repairmatch_backend.identity;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Compares the name a user typed in their profile with the name read from their identity document.
 * Pure and stateless: the document name is only ever an argument and is never stored or logged.
 */
public final class NameMatcher {

    private NameMatcher() {
    }

    /**
     * @return {@code null} when either name is missing (nothing to compare); {@code false} when the profile
     *         has fewer than two tokens or some profile token is absent from the document name; {@code true}
     *         when every profile token appears in the document name (order and extra names do not matter).
     */
    public static Boolean matches(String profileName, String documentName) {
        Set<String> profile = tokens(profileName);
        Set<String> document = tokens(documentName);
        if (profile.isEmpty() || document.isEmpty()) return null;
        if (profile.size() < 2) return false;
        return document.containsAll(profile);
    }

    static Set<String> tokens(String name) {
        if (name == null) return Set.of();
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        if (normalized.isEmpty()) return Set.of();
        return new HashSet<>(Arrays.asList(normalized.split("\\s+")));
    }
}

package com.repairmatch.repairmatch_backend.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns free Spanish text into comparable tokens: lower case, no accents, no
 * stop words, and a light stem (plural endings removed, long words cut) so
 * "enfría", "enfriaba" and "enfriar" land on the same feature.
 */
public final class TextNormalizer {
    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    private static final int STEM_LENGTH = 6;

    static final Set<String> STOP_WORDS = Set.of(
            "a", "al", "algo", "como", "con", "de", "del", "desde", "el", "ella", "en", "es", "esta", "este",
            "esto", "hay", "la", "las", "le", "lo", "los", "me", "mi", "mis", "muy", "para", "pero", "por",
            "que", "se", "si", "su", "sus", "tiene", "un", "una", "uno", "y", "ya", "o", "hace", "cuando",
            "tengo", "estoy", "solo", "sale", "todo", "toda", "cada", "veces", "poco", "mucho");

    private TextNormalizer() {
    }

    /** Lower case without accents: "Refrigeradora LG, no enfría" -> "refrigeradora lg, no enfria". */
    public static String fold(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /** Raw folded words, stop words included (used for phrase rules). */
    public static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(fold(text));
        while (matcher.find()) words.add(matcher.group());
        return words;
    }

    /** Features for the classifier: folded, without stop words, stemmed, plus "no_<word>" negation pairs. */
    public static List<String> features(String text) {
        List<String> words = words(text);
        List<String> features = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            if (word.equals("no") && i + 1 < words.size()) {
                // "no enfria" and "enfria" mean opposite things: keep the negation as its own feature.
                features.add("no_" + stem(words.get(i + 1)));
                i++; // the negated word only counts as part of the pair
                continue;
            }
            if (STOP_WORDS.contains(word) || word.length() < 2) continue;
            features.add(stem(word));
        }
        return features;
    }

    static String stem(String word) {
        String w = word;
        if (w.length() > 4 && w.endsWith("es")) w = w.substring(0, w.length() - 2);
        else if (w.length() > 3 && w.endsWith("s")) w = w.substring(0, w.length() - 1);
        return w.length() > STEM_LENGTH ? w.substring(0, STEM_LENGTH) : w;
    }
}

package com.repairmatch.repairmatch_backend.ai;

import com.repairmatch.repairmatch_backend.model.Request;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a free-text search ("la refri que gotea desde la semana pasada") and
 * turns it into structured filters: appliance kind, symptom, request status,
 * age and brand. Uses the same local classifiers as {@link FaultAnalyzer}.
 */
@Component
public class SearchInterpreter {
    /**
     * Twice the uniform chance over the symptoms (1/10 each): with one
     * informative word the right class wins at about 0.26, well above chance.
     */
    static final double SEARCH_MIN_SYMPTOM_CONFIDENCE = 2.0 / Symptom.values().length;
    private static final Pattern AGO = Pattern.compile("hace (\\d{1,3}) (dia|dias|semana|semanas|mes|meses)");
    /** Word prefixes that name an appliance, so plurals and variants count ("lavadoras", "refris"). */
    private static final List<String> APPLIANCE_PREFIXES = List.of(
            "refri", "nevera", "frigid", "congelador", "freezer", "lavador", "lavarop", "secador");

    public record Interpretation(
            Optional<ApplianceKind> appliance,
            Optional<Symptom> symptom,
            Optional<Request.RequestStatus> status,
            Optional<Integer> sinceDays,
            Optional<String> brand,
            List<String> keywords) {

        public boolean isEmpty() {
            return appliance.isEmpty() && symptom.isEmpty() && status.isEmpty()
                    && sinceDays.isEmpty() && brand.isEmpty() && keywords.isEmpty();
        }
    }

    private final FaultAnalyzer analyzer;

    public SearchInterpreter(FaultAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public Interpretation interpret(String query) {
        String folded = TextNormalizer.fold(query);
        Optional<ApplianceKind> appliance = analyzer.classifyAppliance(query)
                .filter(g -> g.confidence() >= FaultAnalyzer.MIN_CONFIDENCE && mentionsAppliance(folded))
                .map(FaultAnalyzer.Guess::value);
        // A search is short ("la refri que gotea"), so one symptom word is all the
        // evidence there is: accept a lower posterior, but only together with
        // the second guard (the query names one of that symptom's own words).
        Optional<Symptom> symptom = analyzer.classifySymptom(query)
                .filter(g -> g.confidence() >= SEARCH_MIN_SYMPTOM_CONFIDENCE && mentionsSymptom(folded, g.value()))
                .map(FaultAnalyzer.Guess::value);
        Optional<Request.RequestStatus> status = status(folded);
        Optional<Integer> sinceDays = sinceDays(folded);
        Optional<String> brand = analyzer.detectBrand(query);

        // Free words only when nothing structured was understood, so a
        // well-understood query is not narrowed by leftover words.
        List<String> keywords = new ArrayList<>();
        if (appliance.isEmpty() && symptom.isEmpty() && status.isEmpty() && brand.isEmpty()) {
            Set<String> unique = new LinkedHashSet<>();
            for (String word : TextNormalizer.words(query)) {
                if (word.length() >= 4 && !TextNormalizer.STOP_WORDS.contains(word) && !isTimeWord(word)) unique.add(word);
            }
            keywords.addAll(unique.stream().limit(4).toList());
        }
        return new Interpretation(appliance, symptom, status, sinceDays, brand, List.copyOf(keywords));
    }

    private static boolean mentionsAppliance(String folded) {
        for (String word : TextNormalizer.words(folded)) {
            for (String prefix : APPLIANCE_PREFIXES) {
                if (word.startsWith(prefix)) return true;
            }
        }
        return false;
    }

    /** Guards against weak guesses: the query must contain one of the symptom's own words. */
    private static boolean mentionsSymptom(String folded, Symptom symptom) {
        for (String term : symptom.searchTerms()) {
            if (folded.contains(term)) return true;
        }
        return false;
    }

    static Optional<Request.RequestStatus> status(String folded) {
        if (folded.matches(".*\\b(cancelad[ao]s?|anulad[ao]s?)\\b.*")) return Optional.of(Request.RequestStatus.CANCELADA);
        if (folded.matches(".*\\b(cerrad[ao]s?|terminad[ao]s?|reparad[ao]s?|resuelt[ao]s?)\\b.*")) return Optional.of(Request.RequestStatus.CERRADA);
        if (folded.matches(".*\\b(con propuestas?|con ofertas?|propuestas|ofertas)\\b.*")) return Optional.of(Request.RequestStatus.CON_PROPUESTAS);
        if (folded.matches(".*\\b(expirad[ao]s?|vencid[ao]s?)\\b.*")) return Optional.of(Request.RequestStatus.EXPIRADA);
        if (folded.matches(".*\\b(publicad[ao]s?|pendientes?|esperando|sin propuestas?)\\b.*")) return Optional.of(Request.RequestStatus.PUBLICADA);
        return Optional.empty();
    }

    static Optional<Integer> sinceDays(String folded) {
        Matcher ago = AGO.matcher(folded);
        if (ago.find()) {
            int n = Integer.parseInt(ago.group(1));
            String unit = ago.group(2);
            int days = unit.startsWith("dia") ? n : unit.startsWith("semana") ? n * 7 : n * 31;
            return Optional.of(Math.max(1, Math.min(days, 3650)));
        }
        if (folded.matches(".*\\bhoy\\b.*")) return Optional.of(1);
        if (folded.matches(".*\\bayer\\b.*")) return Optional.of(2);
        if (folded.contains("semana pasada") || folded.contains("hace una semana")) return Optional.of(14);
        if (folded.contains("esta semana")) return Optional.of(7);
        if (folded.contains("mes pasado") || folded.contains("hace un mes")) return Optional.of(62);
        if (folded.contains("este mes")) return Optional.of(31);
        return Optional.empty();
    }

    private static boolean isTimeWord(String word) {
        return Set.of("hace", "semana", "pasada", "ayer", "dias", "mes", "meses", "desde").contains(word);
    }
}

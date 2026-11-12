package com.repairmatch.repairmatch_backend.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures the local model on a held-out set: phrasings written apart from the
 * training file and checked NOT to appear in it, so the score reflects
 * generalization, not memorization. The thresholds are the bar the model must
 * keep as the training data changes.
 */
class HeldOutAccuracyTest {
    private final FaultAnalyzer analyzer = new FaultAnalyzer();

    private static final Map<String, ApplianceKind> APPLIANCES = Map.ofEntries(
            Map.entry("mi refri ya no mantiene frías las cosas", ApplianceKind.REFRIGERADORA),
            Map.entry("la nevera de la cocina suena toda la noche", ApplianceKind.REFRIGERADORA),
            Map.entry("el congelador no congela la carne", ApplianceKind.REFRIGERADORA),
            Map.entry("hay agua acumulada en el fondo del refrigerador", ApplianceKind.REFRIGERADORA),
            Map.entry("la puerta del frigider queda abierta", ApplianceKind.REFRIGERADORA),
            Map.entry("se descongela todo en la refrigeradora", ApplianceKind.REFRIGERADORA),
            Map.entry("el refrigerador samsung hace un ruido extraño", ApplianceKind.REFRIGERADORA),
            Map.entry("la refri bota agua por delante", ApplianceKind.REFRIGERADORA),
            Map.entry("la lavadora se apaga a mitad del ciclo", ApplianceKind.LAVADORA),
            Map.entry("mi lavadora no exprime bien la ropa", ApplianceKind.LAVADORA),
            Map.entry("el tambor de la lavadora suena metálico", ApplianceKind.LAVADORA),
            Map.entry("la lavadora tiene una fuga por la manguera", ApplianceKind.LAVADORA),
            Map.entry("no termina de lavar, se queda en enjuague", ApplianceKind.LAVADORA),
            Map.entry("la lavadora samsung no llena de agua", ApplianceKind.LAVADORA),
            Map.entry("mi lavaropas no arranca", ApplianceKind.LAVADORA),
            Map.entry("la ropa queda con jabón después de lavar", ApplianceKind.LAVADORA));

    private static final Map<String, Symptom> SYMPTOMS = Map.ofEntries(
            Map.entry("las cosas ya no se mantienen frías", Symptom.NO_ENFRIA),
            Map.entry("no está enfriando como antes", Symptom.NO_ENFRIA),
            Map.entry("el congelador está lleno de hielo", Symptom.ESCARCHA),
            Map.entry("aparece escarcha gruesa en el fondo", Symptom.ESCARCHA),
            Map.entry("moja toda la cocina con agua", Symptom.FUGA_AGUA),
            Map.entry("hay goteo constante y un charco", Symptom.FUGA_AGUA),
            Map.entry("no da señal de vida, no prende", Symptom.NO_ENCIENDE),
            Map.entry("no se enciende la pantalla", Symptom.NO_ENCIENDE),
            Map.entry("suena metálico cuando gira", Symptom.RUIDO),
            Map.entry("vibra demasiado y hace bulla", Symptom.RUIDO),
            Map.entry("no exprime bien la ropa", Symptom.NO_CENTRIFUGA),
            Map.entry("la ropa sale chorreando de mojada", Symptom.NO_CENTRIFUGA),
            Map.entry("el agua no se va al terminar", Symptom.NO_DESAGUA),
            Map.entry("queda agua estancada en el tambor", Symptom.NO_DESAGUA),
            Map.entry("no le llega agua para lavar", Symptom.NO_CARGA_AGUA),
            Map.entry("no entra el agua al tambor", Symptom.NO_CARGA_AGUA),
            Map.entry("la puerta queda abierta", Symptom.PUERTA),
            Map.entry("el empaque está roto", Symptom.PUERTA),
            Map.entry("tiene olor a humedad", Symptom.MAL_OLOR),
            Map.entry("huele a quemado por dentro", Symptom.MAL_OLOR));

    @Test
    void heldOutPhrasesAreNotInTheTrainingFile() throws IOException {
        String training;
        try (InputStream in = getClass().getResourceAsStream("/ai/training-es.tsv")) {
            training = TextNormalizer.fold(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        for (String phrase : concat(APPLIANCES.keySet(), SYMPTOMS.keySet())) {
            assertThat(training).as("held-out phrase leaked into training: %s", phrase)
                    .doesNotContain(TextNormalizer.fold(phrase));
        }
    }

    @Test
    void classifiesTheApplianceOfMostHeldOutPhrases() {
        double accuracy = accuracy(APPLIANCES, text -> analyzer.classifyAppliance(text).map(FaultAnalyzer.Guess::value).orElse(null));
        System.out.printf("Held-out appliance accuracy: %.2f%n", accuracy);
        assertThat(accuracy).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void classifiesTheSymptomOfMostHeldOutPhrases() {
        double accuracy = accuracy(SYMPTOMS, text -> analyzer.classifySymptom(text).map(FaultAnalyzer.Guess::value).orElse(null));
        System.out.printf("Held-out symptom accuracy: %.2f%n", accuracy);
        assertThat(accuracy).isGreaterThanOrEqualTo(0.70);
    }

    private static <L> double accuracy(Map<String, L> cases, java.util.function.Function<String, L> predict) {
        long right = cases.entrySet().stream().filter(c -> c.getValue() == predict.apply(c.getKey())).count();
        return right / (double) cases.size();
    }

    private static List<String> concat(java.util.Set<String> a, java.util.Set<String> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}

package com.repairmatch.repairmatch_backend.ai;

import com.repairmatch.repairmatch_backend.model.Request;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Behaviour of the local model (generalization is measured in {@link HeldOutAccuracyTest}). */
class FaultAnalyzerTest {
    private final FaultAnalyzer analyzer = new FaultAnalyzer();
    private final SearchInterpreter interpreter = new SearchInterpreter(analyzer);

    @Test
    void keepsNegationAcrossPronouns() {
        assertThat(TextNormalizer.features("no se enciende")).containsExactly("no_encien");
        assertThat(analyzer.classifySymptom("no se enciende para nada").orElseThrow().value()).isEqualTo(Symptom.NO_ENCIENDE);
    }

    @Test
    void extractsBrandAndModelAndToleratesOneTypo() {
        var analysis = analyzer.analyze("Mi refri Samsumg RT38K5930SL no enfría desde las 9:00");
        assertThat(analysis.brand()).contains("Samsung");
        assertThat(analysis.model()).contains("RT38K5930SL");

        assertThat(analyzer.analyze("lavadora LG WM22VV2S6R").brand()).contains("LG");
        assertThat(analyzer.analyze("lavadora lg").model()).isEmpty();
        // Quantities and ordinals are not model numbers; everyday words are not brands.
        assertThat(analyzer.analyze("lavadora de 10kg a 220v desde las 9am").model()).isEmpty();
        assertThat(analyzer.analyze("no quiere hacer hielo").brand()).isEmpty();
        assertThat(analyzer.analyze("la media hora que demora").brand()).isEmpty();
    }

    @Test
    void saysNothingWhenTheTextHasNothingToGoOn() {
        var analysis = analyzer.analyze("hola, buenas tardes");
        assertThat(analysis.appliance()).isEmpty();
        assertThat(analysis.symptom()).isEmpty();
        assertThat(analysis.brand()).isEmpty();
        assertThat(analyzer.analyze("").appliance()).isEmpty();
        assertThat(analyzer.analyze(null).symptom()).isEmpty();
    }

    @Test
    void naiveBayesReturnsProbabilitiesThatAddUp() {
        var classifier = new NaiveBayesClassifier<>(List.of(
                new NaiveBayesClassifier.Example<>("perro ladra", "animal"),
                new NaiveBayesClassifier.Example<>("auto frena", "vehiculo")));
        var prediction = classifier.predict("el perro ladra fuerte").orElseThrow();
        assertThat(prediction.label()).isEqualTo("animal");
        assertThat(prediction.probability()).isBetween(0.5, 1.0);
        assertThat(classifier.predict("zzz")).isEmpty();
    }

    @Test
    void normalizesSpanishText() {
        assertThat(TextNormalizer.fold("Refrigeradora LG, ¡NO ENFRÍA!")).isEqualTo("refrigeradora lg, ¡no enfria!");
        assertThat(TextNormalizer.features("no enfría la refri")).containsExactly("no_enfria", "refri");
        assertThat(FaultAnalyzer.levenshtein("samsumg", "samsung")).isEqualTo(1);
        assertThat(FaultAnalyzer.levenshtein("smsng", "samsung")).isEqualTo(2);
    }

    @Test
    void interpretsANaturalLanguageSearch() {
        var understood = interpreter.interpret("la refri que gotea desde la semana pasada");
        assertThat(understood.appliance()).contains(ApplianceKind.REFRIGERADORA);
        assertThat(understood.symptom()).contains(Symptom.FUGA_AGUA);
        assertThat(understood.sinceDays()).contains(14);
        assertThat(understood.keywords()).isEmpty();

        var status = interpreter.interpret("mis lavadoras canceladas");
        assertThat(status.appliance()).contains(ApplianceKind.LAVADORA);
        assertThat(status.status()).contains(Request.RequestStatus.CANCELADA);

        assertThat(interpreter.interpret("solicitudes con propuestas").status()).contains(Request.RequestStatus.CON_PROPUESTAS);
        assertThat(interpreter.interpret("solicitudes sin propuestas").status()).contains(Request.RequestStatus.PUBLICADA);
        assertThat(interpreter.interpret("las de hoy").sinceDays()).contains(0);
        assertThat(interpreter.interpret("las de ayer").sinceDays()).contains(1);
        assertThat(interpreter.interpret("tengo un paraguas mojado").symptom()).isEmpty();
        assertThat(interpreter.interpret("hace 3 dias").sinceDays()).contains(3);
        assertThat(interpreter.interpret("hace 2 meses").sinceDays()).contains(62);
        assertThat(interpreter.interpret("samsung").brand()).contains("Samsung");
    }

    @Test
    void fallsBackToKeywordsWhenItUnderstandsNothing() {
        var understood = interpreter.interpret("cocina departamento");
        assertThat(understood.appliance()).isEmpty();
        assertThat(understood.keywords()).containsExactly("cocina", "departamento");
        assertThat(interpreter.interpret("de la que").isEmpty()).isTrue();
    }
}

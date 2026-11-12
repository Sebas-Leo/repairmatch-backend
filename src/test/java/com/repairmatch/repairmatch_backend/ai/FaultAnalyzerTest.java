package com.repairmatch.repairmatch_backend.ai;

import com.repairmatch.repairmatch_backend.model.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The local model, on phrasings that are NOT in the training file (it must generalize, not memorize). */
class FaultAnalyzerTest {
    private final FaultAnalyzer analyzer = new FaultAnalyzer();
    private final SearchInterpreter interpreter = new SearchInterpreter(analyzer);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mi refri Samsung ya no enfría nada, la luz sí prende | REFRIGERADORA",
            "el refrigerador gotea agua debajo de los cajones | REFRIGERADORA",
            "se forma mucho hielo en el congelador | REFRIGERADORA",
            "La lavadora LG no centrifuga y la ropa queda empapada | LAVADORA",
            "mi lavadora suena fuerte cuando lava | LAVADORA",
            "no bota el agua al terminar el lavado | LAVADORA"
    })
    void classifiesTheApplianceOfUnseenDescriptions(String text, ApplianceKind expected) {
        var guess = analyzer.classifyAppliance(text).orElseThrow();
        assertThat(guess.value()).isEqualTo(expected);
        assertThat(guess.confidence()).isGreaterThanOrEqualTo(FaultAnalyzer.MIN_CONFIDENCE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "dejó de enfriar desde el lunes | NO_ENFRIA",
            "hay un charco debajo, gotea bastante | FUGA_AGUA",
            "no prende para nada desde ayer | NO_ENCIENDE",
            "hace un ruido fuerte y vibra | RUIDO",
            "la ropa sale empapada, no centrifuga | NO_CENTRIFUGA",
            "se queda llena de agua y no desagua | NO_DESAGUA",
            "el jebe de la puerta está despegado | PUERTA",
            "huele horrible por dentro | MAL_OLOR"
    })
    void classifiesTheSymptomOfUnseenDescriptions(String text, Symptom expected) {
        assertThat(analyzer.classifySymptom(text).orElseThrow().value()).isEqualTo(expected);
    }

    @Test
    void extractsBrandAndModelAndToleratesOneTypo() {
        var analysis = analyzer.analyze("Mi refri Samsumg RT38K5930SL no enfría desde las 9:00");
        assertThat(analysis.brand()).contains("Samsung");
        assertThat(analysis.model()).contains("RT38K5930SL");

        assertThat(analyzer.analyze("lavadora LG WM22VV2S6R").brand()).contains("LG");
        assertThat(analyzer.analyze("lavadora lg").model()).isEmpty();
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

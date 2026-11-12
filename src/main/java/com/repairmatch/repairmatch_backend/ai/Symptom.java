package com.repairmatch.repairmatch_backend.ai;

import java.util.List;

/**
 * Normalized fault symptoms the classifier recognizes. {@code searchTerms} are
 * folded words a request description must contain (any of them) to match the
 * symptom in a search.
 */
public enum Symptom {
    NO_ENFRIA("No enfría", List.of("enfria", "enfriar", "frio", "caliente", "tibia", "tibio", "calienta")),
    ESCARCHA("Escarcha o hielo", List.of("escarcha", "hielo", "congela", "congelado")),
    FUGA_AGUA("Fuga de agua", List.of("gotea", "fuga", "chorrea", "moja", "charco", "agua")),
    NO_ENCIENDE("No enciende", List.of("enciende", "prende", "arranca", "funciona", "muerta")),
    RUIDO("Hace ruido", List.of("ruido", "suena", "golpe", "golpeteo", "vibra", "zumbido", "sonido")),
    NO_CENTRIFUGA("No centrifuga", List.of("centrifuga", "exprime", "empapada", "mojada", "gira")),
    NO_DESAGUA("No bota el agua", List.of("bota", "desagua", "desague", "drena", "llena", "estancada")),
    NO_CARGA_AGUA("No carga agua", List.of("carga", "entra", "ingresa", "llenado")),
    PUERTA("Puerta o sello", List.of("puerta", "jebe", "empaque", "cierra", "sello", "tapa")),
    MAL_OLOR("Mal olor", List.of("olor", "huele", "apesta", "hongos"));

    private final String label;
    private final List<String> searchTerms;

    Symptom(String label, List<String> searchTerms) {
        this.label = label;
        this.searchTerms = searchTerms;
    }

    public String label() {
        return label;
    }

    public List<String> searchTerms() {
        return searchTerms;
    }
}

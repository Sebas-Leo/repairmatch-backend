package com.repairmatch.repairmatch_backend.ai;

import java.util.regex.Pattern;

/** The appliance families of the MVP, with the catalog names they map to. */
public enum ApplianceKind {
    LAVADORA("Lavadora", Pattern.compile("(?i)lavad|wash")),
    REFRIGERADORA("Refrigeradora", Pattern.compile("(?i)refrig|fridge|nevera"));

    private final String label;
    private final Pattern catalogName;

    ApplianceKind(String label, Pattern catalogName) {
        this.label = label;
        this.catalogName = catalogName;
    }

    public String label() {
        return label;
    }

    /** True when an appliance type of the catalog ("Washing machine", "Lavadora") is this kind. */
    public boolean matchesCatalogName(String name) {
        return name != null && catalogName.matcher(name).find();
    }
}

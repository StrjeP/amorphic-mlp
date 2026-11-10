package dev.amorphic.demo;

/** Catalogue addresses of the tooling running in this app (see amorphic-demo-env/catalogue.yaml). */
final class ToolingAddresses {
    static final String INVENTORY = "pkg:amorphic/inventory@0.1?lang=java";
    static final String CATALOGUE = "pkg:amorphic/catalogue@1.0?lang=yaml";
    static final String GOVERNOR = "pkg:amorphic/governor@0.1?lang=java";

    private ToolingAddresses() {}
}

package dev.amorphic.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The real catalogue in amorphic-demo-env must load and say what the demo relies on. */
class CatalogueTest {

    final Catalogue catalogue = Catalogue.load(Path.of("../../amorphic-demo-env/catalogue.yaml"));

    @Test
    void listsEveryPricingCalcVersion() {
        assertThat(catalogue.versions("pricing-calc")).extracting(Catalogue.Version::address).containsExactly(
                "pkg:amorphic/pricing-calc@1.0?lang=python",
                "pkg:amorphic/pricing-calc@2.0?lang=java",
                "pkg:amorphic/pricing-calc@2.1?lang=java");
    }

    @Test
    void listsTheToolingAndMarksWhatIsNotBuiltYet() {
        assertThat(catalogue.kind("pricing-calc")).isEqualTo("capability");
        for (String tool : new String[] {"catalogue", "inventory", "governor"}) {
            assertThat(catalogue.kind(tool)).as(tool).isEqualTo("tooling");
            assertThat(catalogue.versions(tool)).as(tool).hasSize(1);
        }
        var inventory = catalogue.versions("inventory").get(0);
        assertThat(inventory.allows("local")).isTrue();
        assertThat(inventory.allows("remote")).as("remote comes with phase 4").isFalse();
        assertThat(catalogue.versions("governor").get(0).isPlanned()).isTrue();
        assertThat(inventory.isPlanned()).isFalse();
    }

    @Test
    void twoPointZeroIsWithdrawnAndOnlyTwoPointOneMayRunLocally() {
        var v20 = catalogue.find("pricing-calc", "pkg:amorphic/pricing-calc@2.0?lang=java").orElseThrow();
        var v21 = catalogue.find("pricing-calc", "pkg:amorphic/pricing-calc@2.1?lang=java").orElseThrow();
        var v10 = catalogue.find("pricing-calc", "pkg:amorphic/pricing-calc@1.0?lang=python").orElseThrow();
        assertThat(v20.isWithdrawn()).isTrue();
        assertThat(v21.isWithdrawn()).isFalse();
        assertThat(v21.allows("local")).isTrue();
        assertThat(v10.allows("local")).isFalse();
    }
}

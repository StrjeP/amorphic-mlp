package dev.amorphic.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Resolution;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

/** The real catalogue in amorphic-demo-env must load and say what the demo relies on. */
class CatalogueTest {

    final Catalogue catalogue = Catalogue.load(Path.of("../../amorphic-demo-env/catalogue.yaml"));

    @Test
    void listsEveryPricingCalcVersion() {
        assertThat(catalogue.versions("pricing-calc")).extracting(v -> v.address().toString()).containsExactly(
                "pkg:amorphic/pricing-calc@1.0?lang=python",
                "pkg:amorphic/pricing-calc@2.0?lang=java",
                "pkg:amorphic/pricing-calc@2.1?lang=java");
    }

    @Test
    void listsTheToolingAndWhereItMayRun() {
        assertThat(catalogue.kind("pricing-calc")).isEqualTo("capability");
        for (String tool : new String[] {"catalogue", "inventory", "governor"}) {
            assertThat(catalogue.kind(tool)).as(tool).isEqualTo("tooling");
            assertThat(catalogue.versions(tool)).as(tool).hasSize(1);
        }
        // Both may run in-process in the app or as their own service, at the service URL the table uses.
        for (String tool : new String[] {"inventory", "governor"}) {
            var v = catalogue.versions(tool).get(0);
            assertThat(v.isPlanned()).as(tool).isFalse();
            assertThat(v.allows("local")).as(tool).isTrue();
            assertThat(v.allows("remote")).as(tool).isTrue();
            assertThat(v.service().url()).as(tool).isEqualTo("http://" + tool + ":8080");
        }
    }

    @Test
    void twoPointZeroIsWithdrawnAndOnlyTwoPointOneMayRunLocally() {
        var v20 = catalogue.find("pricing-calc", Address.parse("pkg:amorphic/pricing-calc@2.0?lang=java")).orElseThrow();
        var v21 = catalogue.find("pricing-calc", Address.parse("pkg:amorphic/pricing-calc@2.1?lang=java")).orElseThrow();
        var v10 = catalogue.find("pricing-calc", Address.parse("pkg:amorphic/pricing-calc@1.0?lang=python")).orElseThrow();
        assertThat(v20.isWithdrawn()).isTrue();
        assertThat(v21.isWithdrawn()).isFalse();
        assertThat(v21.allows("local")).isTrue();
        assertThat(v10.allows("local")).isFalse();
    }

    @Test
    void resolvesTheNewestVersionThatIsNotWithdrawn() {
        assertThat(catalogue.resolve(Address.parse("pkg:amorphic/pricing-calc@*")).chosen().orElseThrow().address())
                .hasToString("pkg:amorphic/pricing-calc@2.1?lang=java");
        // 2.0 is withdrawn: a pinned request still finds it, with a warning.
        var pinned = (Resolution.Resolved<Catalogue.Version>) catalogue.resolve(
                Address.parse("pkg:amorphic/pricing-calc@2.0?lang=java"));
        assertThat(pinned.warnings()).singleElement().asString().contains("withdrawn");
        assertThat(catalogue.resolve(Address.parse("pkg:amorphic/pricing-calc@3.*")))
                .isInstanceOfSatisfying(Resolution.NoMatch.class, n -> assertThat(n.reason())
                        .startsWith("nothing matches pkg:amorphic/pricing-calc@3.*; available: "));
    }

    @Test
    void aBadAddressFailsTheLoad(@TempDir Path dir) throws Exception {
        Path bad = dir.resolve("catalogue.yaml");
        Files.writeString(bad, """
                capabilities:
                  thing:
                    kind: capability
                    versions:
                      - address: pkg:amorphic/thing@one?lang=java
                        locations: [remote]
                """);
        assertThatThrownBy(() -> Catalogue.load(bad)).hasMessageContaining("cannot read catalogue " + bad)
                .hasMessageContaining("'pkg:amorphic/thing@one?lang=java' is not a valid address");
    }
}

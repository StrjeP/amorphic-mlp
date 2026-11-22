package dev.amorphic.addressing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResolverTest {

    record Entry(Address address, boolean isWithdrawn, boolean isPlanned) implements Candidate {
        static Entry of(String a) {
            return new Entry(Address.parse(a), false, false);
        }

        static Entry withdrawn(String a) {
            return new Entry(Address.parse(a), true, false);
        }

        static Entry planned(String a) {
            return new Entry(Address.parse(a), false, true);
        }
    }

    static final List<Entry> CATALOGUE = List.of(
            Entry.of("pkg:amorphic/greeter@1.0?lang=python"),
            Entry.withdrawn("pkg:amorphic/greeter@2.0?lang=java"),
            Entry.of("pkg:amorphic/greeter@1.9?lang=java"),
            Entry.planned("pkg:amorphic/greeter@3.0?lang=java"),
            Entry.of("pkg:amorphic/farewell@9.0?lang=java"));

    static Resolution<Entry> resolve(String request) {
        return Resolver.resolve(Address.parse(request), CATALOGUE, Preference.HIGHEST_VERSION);
    }

    @Test
    void choosesTheHighestVersionThatMatches() {
        assertThat(resolve("pkg:amorphic/greeter@*").chosen().orElseThrow().address())
                .hasToString("pkg:amorphic/greeter@1.9?lang=java");
        assertThat(resolve("pkg:amorphic/greeter").chosen().orElseThrow().address())
                .hasToString("pkg:amorphic/greeter@1.9?lang=java");
        assertThat(resolve("pkg:amorphic/greeter@1.*?lang=python").chosen().orElseThrow().address())
                .hasToString("pkg:amorphic/greeter@1.0?lang=python");
    }

    @Test
    void aFloatingRequestSkipsWithdrawnAndPlannedVersions() {
        // 2.0 is withdrawn and 3.0 is planned, so neither is chosen even though both are higher.
        assertThat(resolve("pkg:amorphic/greeter@*?lang=java").chosen().orElseThrow().address().version())
                .hasValue(Version.parse("1.9"));
        assertThat(resolve("pkg:amorphic/greeter@2.*")).isInstanceOf(Resolution.NoMatch.class);
    }

    @Test
    void aPinnedRequestResolvesAWithdrawnVersionWithAWarning() {
        var r = (Resolution.Resolved<Entry>) resolve("pkg:amorphic/greeter@2.0?lang=java");
        assertThat(r.candidate().isWithdrawn()).isTrue();
        assertThat(r.warnings()).singleElement().asString().contains("withdrawn").contains("asked for exactly");
    }

    @Test
    void aPlannedVersionIsNeverResolved() {
        assertThat(resolve("pkg:amorphic/greeter@3.0?lang=java")).isInstanceOf(Resolution.NoMatch.class);
    }

    @Test
    void noMatchNamesWhatWasAskedForAndWhatIsAvailable() {
        var none = (Resolution.NoMatch<Entry>) resolve("pkg:amorphic/greeter@4.*");
        assertThat(none.reason()).isEqualTo("nothing matches pkg:amorphic/greeter@4.*; available: "
                + "pkg:amorphic/greeter@1.0?lang=python, pkg:amorphic/greeter@2.0?lang=java (withdrawn), "
                + "pkg:amorphic/greeter@1.9?lang=java, pkg:amorphic/greeter@3.0?lang=java (planned)");
        assertThat(((Resolution.NoMatch<Entry>) resolve("pkg:amorphic/unknown@*")).reason())
                .isEqualTo("nothing matches pkg:amorphic/unknown@*; available: none");
    }

    @Test
    void tiesGoToTheFirstInCanonicalOrder() {
        var tied = List.of(Entry.of("pkg:amorphic/greeter@1.0?lang=python"), Entry.of("pkg:amorphic/greeter@1.0?lang=java"));
        var r = Resolver.resolve(Address.parse("pkg:amorphic/greeter@*"), tied, Preference.HIGHEST_VERSION);
        assertThat(r.chosen().orElseThrow().address()).hasToString("pkg:amorphic/greeter@1.0?lang=java");
    }
}

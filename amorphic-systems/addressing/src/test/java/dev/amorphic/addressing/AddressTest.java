package dev.amorphic.addressing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class AddressTest {

    static final String GREETER = "pkg:amorphic/greeter@1.0.1?lang=java";

    @Test
    void readsEveryPart() {
        Address a = Address.parse(GREETER);
        assertThat(a.kind()).isEqualTo("amorphic");
        assertThat(a.name()).isEqualTo("greeter");
        assertThat(a.version()).hasValue(Version.parse("1.0.1"));
        assertThat(a.range()).isEmpty();
        assertThat(a.qualifier("lang")).hasValue("java");
        assertThat(a.isPinned()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pkg:amorphic/greeter", "pkg:amorphic/greeter@1.*", "pkg:amorphic/greeter@*",
            "pkg:amorphic/greeter@1.0.1", GREETER, "pkg:tooling/catalogue@1.0?lang=yaml"})
    void printsBackExactlyWhatItRead(String text) {
        assertThat(Address.parse(text)).hasToString(text);
    }

    @Test
    void canonicalFormSortsQualifiersByKey() {
        Address a = Address.parse("pkg:amorphic/greeter@1.0.1?lang=java&arch=arm64");
        assertThat(a).hasToString("pkg:amorphic/greeter@1.0.1?arch=arm64&lang=java");
        assertThat(a).isEqualTo(Address.parse("pkg:amorphic/greeter@1.0.1?arch=arm64&lang=java"));
        assertThat(a.hashCode()).isEqualTo(Address.parse("pkg:amorphic/greeter@1.0.1?arch=arm64&lang=java").hashCode());
    }

    @Test
    void namesItsRungOnTheLadder() {
        assertThat(Address.parse("pkg:amorphic/greeter").rung()).isEqualTo(Rung.CAPABILITY);
        assertThat(Address.parse("pkg:amorphic/greeter@1.*").rung()).isEqualTo(Rung.RANGE);
        assertThat(Address.parse("pkg:amorphic/greeter@1.0.1").rung()).isEqualTo(Rung.VERSION);
        assertThat(Address.parse(GREETER).rung()).isEqualTo(Rung.IMPLEMENTATION);
    }

    @Test
    void broadensOneRungAtATime() {
        Address a = Address.parse(GREETER);
        Address version = a.broaden().orElseThrow();
        Address range = version.broaden().orElseThrow();
        Address capability = range.broaden().orElseThrow();
        assertThat(version).hasToString("pkg:amorphic/greeter@1.0.1");
        assertThat(range).hasToString("pkg:amorphic/greeter@1.*");
        assertThat(capability).hasToString("pkg:amorphic/greeter");
        assertThat(capability.broaden()).isEmpty();
    }

    @Test
    void eachRungNarrowsTheOneAboveIt() {
        Address capability = Address.parse("pkg:amorphic/greeter");
        Address range = Address.parse("pkg:amorphic/greeter@1.*");
        Address version = Address.parse("pkg:amorphic/greeter@1.0.1");
        Address implementation = Address.parse(GREETER);

        assertThat(implementation.narrows(version)).isTrue();
        assertThat(version.narrows(range)).isTrue();
        assertThat(range.narrows(capability)).isTrue();
        assertThat(implementation.narrows(capability)).isTrue();

        assertThat(version.narrows(implementation)).isFalse();
        assertThat(version.narrows(version)).isFalse();
        assertThat(Address.parse("pkg:amorphic/greeter@1.1.*").narrows(range)).isTrue();
        assertThat(Address.parse("pkg:amorphic/greeter@2.0").narrows(range)).isFalse();
        assertThat(Address.parse("pkg:amorphic/farewell@1.0.1").narrows(capability)).isFalse();
    }

    @Test
    void aRequestMatchesCandidatesThatHaveEverythingItAsksFor() {
        Address anyGreeter = Address.parse("pkg:amorphic/greeter@*");
        assertThat(anyGreeter.matches(Address.parse(GREETER))).isTrue();
        assertThat(anyGreeter.matches(Address.parse("pkg:amorphic/greeter@2.0?lang=python"))).isTrue();
        assertThat(Address.parse("pkg:amorphic/greeter?lang=java").matches(Address.parse(GREETER))).isTrue();
        assertThat(Address.parse("pkg:amorphic/greeter?lang=go").matches(Address.parse(GREETER))).isFalse();
        // A candidate must name a version.
        assertThat(anyGreeter.matches(Address.parse("pkg:amorphic/greeter@1.*"))).isFalse();
    }

    @Test
    void buildsRelatedAddresses() {
        Address capability = Address.of("amorphic", "greeter");
        assertThat(capability.at("*")).hasToString("pkg:amorphic/greeter@*");
        assertThat(capability.at("1.0.1").with("lang", "java")).isEqualTo(Address.parse(GREETER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "greeter", "pkg:/greeter", "pkg:amorphic/", "pkg:amorphic/greeter@",
            "pkg:amorphic/greeter@1.x", "pkg:amorphic/greeter@1*", "pkg:amorphic/greeter?lang",
            "pkg:amorphic/greeter?lang=", "pkg:amorphic/greeter?lang=java&lang=go"})
    void refusesWhatItCannotRead(String text) {
        assertThatThrownBy(() -> Address.parse(text)).isInstanceOf(AddressException.class)
                .hasMessageContaining("is not a valid address");
    }

    @Test
    void errorsSayWhatWasExpected() {
        assertThatThrownBy(() -> Address.parse("greeter-1.0"))
                .hasMessage("'greeter-1.0' is not a valid address (expected pkg:<kind>/<name>[@<version>][?<key>=<value>&...])");
        assertThatThrownBy(() -> Address.parse("pkg:amorphic/greeter@1.x"))
                .hasMessageContaining("'1.x' is not a valid version (expected dotted numbers, e.g. 2.1)");
    }
}

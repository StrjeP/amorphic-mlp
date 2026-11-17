package dev.amorphic.addressing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void comparesPartByPartAsNumbers() {
        assertThat(Version.parse("2.10").isNewerThan(Version.parse("2.9"))).isTrue();
        assertThat(Version.parse("1.0.3").compareTo(Version.parse("1.0.12"))).isNegative();
        assertThat(Version.parse("0.1").isNewerThan(Version.parse("0.0.9"))).isTrue();
    }

    @Test
    void aMissingPartIsZeroButTheTextIsKept() {
        assertThat(Version.parse("2.1").compareTo(Version.parse("2.1.0"))).isZero();
        assertThat(Version.parse("2.1")).isNotEqualTo(Version.parse("2.1.0"));
        assertThat(Version.parse("2.1.0")).hasToString("2.1.0");
    }

    @Test
    void theDefaultSchemeIsDottedNumbers() {
        assertThat(Version.parse("1.0").scheme()).isEqualTo(Version.DOTTED);
        assertThatThrownBy(() -> Version.parse("1.0-SNAPSHOT")).isInstanceOf(AddressException.class);
        assertThatThrownBy(() -> Version.parse("9eabf5b")).isInstanceOf(AddressException.class);
    }

    @Test
    void wildcardRanges() {
        Range one = Range.parse("1.*");
        assertThat(one.contains(Version.parse("1"))).isTrue();
        assertThat(one.contains(Version.parse("1.0.1"))).isTrue();
        assertThat(one.contains(Version.parse("2.0"))).isFalse();
        assertThat(Range.parse("*").contains(Version.parse("7.3"))).isTrue();
        assertThat(Range.parse("1.0.*").within(one)).isTrue();
        assertThat(one.within(Range.parse("1.0.*"))).isFalse();
        assertThat(one.within(Range.ANY)).isTrue();
        assertThatThrownBy(() -> Range.parse(">=1.0")).isInstanceOf(AddressException.class)
                .hasMessageContaining("expected a wildcard");
    }
}

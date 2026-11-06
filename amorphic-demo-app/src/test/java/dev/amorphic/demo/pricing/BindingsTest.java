package dev.amorphic.demo.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.amorphic.pricing.PricingCalcCore;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BindingsTest {

    static final AddressTable.Binding LOCAL = new AddressTable.Binding("local", null);

    @Test
    void localBindingRunsCoreInProcess() {
        Address a = Address.parse("pkg:amorphic/pricing-calc@" + PricingCalcCore.VERSION + "?lang=java");
        PricingEndpoint e = Bindings.create(a, LOCAL);
        assertThat(e).isInstanceOf(LocalPricingEndpoint.class);
        assertThat(e.convert(new BigDecimal("100.00"), "GBP", "USD")).isEqualByComparingTo("125.00");
    }

    @Test
    void localBindingRejectsAVersionThatIsNotInProcess() {
        Address a = Address.parse("pkg:amorphic/pricing-calc@9.9?lang=java");
        assertThatThrownBy(() -> Bindings.create(a, LOCAL))
                .isInstanceOf(AddressTableException.class).hasMessageContaining("version 9.9");
    }

    @Test
    void localBindingNeedsJava() {
        Address a = Address.parse("pkg:amorphic/pricing-calc@1.0?lang=python");
        assertThatThrownBy(() -> Bindings.create(a, LOCAL))
                .isInstanceOf(AddressTableException.class).hasMessageContaining("lang=java");
    }

    @Test
    void remoteBindingNeedsAUrl() {
        Address a = Address.parse("pkg:amorphic/pricing-calc@1.0?lang=python");
        assertThatThrownBy(() -> Bindings.create(a, new AddressTable.Binding("remote", null)))
                .isInstanceOf(AddressTableException.class).hasMessageContaining("no url");
    }

    @Test
    void addressParsing() {
        Address a = Address.parse("pkg:amorphic/pricing-calc@2.1?lang=java");
        assertThat(a.name()).isEqualTo("pricing-calc");
        assertThat(a.version()).isEqualTo("2.1");
        assertThat(a.lang()).isEqualTo("java");
        assertThatThrownBy(() -> Address.parse("pricing-calc:2.1")).isInstanceOf(AddressTableException.class);
    }
}

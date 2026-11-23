package dev.amorphic.demo.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.amorphic.access.Invoker;
import dev.amorphic.access.LocalBinding;
import dev.amorphic.addressing.Address;
import dev.amorphic.inventory.Catalogue;
import dev.amorphic.pricing.PricingCalcCore;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BindingsTest {

    static final AddressTable.Binding LOCAL = new AddressTable.Binding("local", null);
    static final AddressTable.Binding REMOTE = new AddressTable.Binding("remote", "http://py-pricing:8000");
    static final Catalogue CATALOGUE = StageAddressTablesTest.CATALOGUE;

    @Test
    void localBindingRunsCoreInProcessThroughTheEnvelope() {
        Address a = Address.parse("pkg:amorphic/pricing-calc@" + PricingCalcCore.VERSION + "?lang=java");
        Invoker e = Bindings.create(a, LOCAL, CATALOGUE);
        assertThat(e).isInstanceOf(LocalBinding.class);
        var reply = e.invoke(a, PricingEnvelopes.request("r1", new BigDecimal("100.00"), "GBP", "USD"));
        assertThat(PricingEnvelopes.amount(reply)).isEqualByComparingTo("125.00");
        assertThat(reply.envelope().payload().schema()).isEqualTo(PricingEnvelopes.REPLY);
    }

    @Test
    void anInProcessRejectionIsAFailureReply() {
        Address a = Address.parse("pkg:amorphic/pricing-calc@" + PricingCalcCore.VERSION + "?lang=java");
        var reply = Bindings.create(a, LOCAL, CATALOGUE).invoke(a, PricingEnvelopes.request("r1", BigDecimal.ONE, "USD", "JPY"));
        assertThat(reply.envelope().isError()).isTrue();
        assertThatThrownBy(() -> PricingEnvelopes.amount(reply)).isInstanceOf(PricingRejectedException.class);
    }

    @Test
    void localBindingRejectsAVersionThatIsNotInProcess() {
        Address a = Address.parse("pkg:amorphic/pricing-calc@9.9?lang=java");
        assertThatThrownBy(() -> Bindings.create(a, LOCAL, CATALOGUE))
                .isInstanceOf(AddressTableException.class).hasMessageContaining("version 9.9");
    }

    @Test
    void localBindingNeedsJava() {
        Address a = Address.parse("pkg:amorphic/pricing-calc@1.0?lang=python");
        assertThatThrownBy(() -> Bindings.create(a, LOCAL, CATALOGUE))
                .isInstanceOf(AddressTableException.class).hasMessageContaining("lang=java");
    }

    @Test
    void remoteBindingNeedsAUrl() {
        Address a = Address.parse("pkg:amorphic/pricing-calc@1.0?lang=python");
        assertThatThrownBy(() -> Bindings.create(a, new AddressTable.Binding("remote", null), CATALOGUE))
                .isInstanceOf(AddressTableException.class).hasMessageContaining("no url");
    }

    @Test
    void aRemoteAddressMustBeCataloguedWithItsAccess() {
        assertThatThrownBy(() -> Bindings.create(Address.parse("pkg:amorphic/pricing-calc@7.0?lang=go"), REMOTE, CATALOGUE))
                .isInstanceOf(AddressTableException.class).hasMessageContaining("is not in the catalogue");

        Address py = Address.parse("pkg:amorphic/pricing-calc@1.0?lang=python");
        Catalogue noAccess = new Catalogue(Map.of("pricing-calc", new Catalogue.Capability("capability", "",
                List.of(new Catalogue.Version(py, List.of("remote"), null, null, null)))), CATALOGUE.schemas());
        assertThatThrownBy(() -> Bindings.create(py, REMOTE, noAccess))
                .isInstanceOf(AddressTableException.class).hasMessageContaining("doesn't say how to reach");
    }

    @Test
    void theSchemasCallsRelyOnMustBeInTheCatalogue() {
        Address py = Address.parse("pkg:amorphic/pricing-calc@1.0?lang=python");
        Catalogue noSchemas = new Catalogue(CATALOGUE.capabilities(), List.of());
        assertThatThrownBy(() -> Bindings.create(py, REMOTE, noSchemas))
                .isInstanceOf(AddressTableException.class)
                .hasMessageContaining("use schema pkg:schema/pricing-calc-convert-request@1.0, which the catalogue doesn't have");
    }
}

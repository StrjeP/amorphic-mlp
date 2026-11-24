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

    static final Address JAVA_21 = Address.parse("pkg:amorphic/pricing-calc@2.1?lang=java");
    static final AddressTable.Binding JAVA_URL = new AddressTable.Binding("remote", "http://java-pricing:8080");

    /** Spec 03, section 6: a lingering 2.0 behind the URL the table now gives to 2.1 is refused, naming both sides. */
    @Test
    void aLingeringOldVersionIsRefused() {
        MetaReader lingering = (url, routed) -> new dev.amorphic.envelope.InstanceRef("java-pricing-v2.0",
                Address.parse("pkg:amorphic/pricing-calc@2.0?lang=java"), null);
        assertThatThrownBy(() -> Bindings.bind(JAVA_21, JAVA_URL, CATALOGUE, StageAddressTablesTest.INSTANCES,
                StageAddressTablesTest.SELF, lingering))
                .isInstanceOf(AddressTableException.class)
                .hasMessage("routed to pkg:amorphic/pricing-calc@2.1?lang=java, but http://java-pricing:8080 is "
                        + "java-pricing-v2.0, which realises pkg:amorphic/pricing-calc@2.0?lang=java");
    }

    @Test
    void aServiceThatCantSayWhoItIsIsRefused() {
        MetaReader silent = (url, routed) -> { throw new dev.amorphic.access.AccessException("connection refused"); };
        assertThatThrownBy(() -> Bindings.bind(JAVA_21, JAVA_URL, CATALOGUE, StageAddressTablesTest.INSTANCES,
                StageAddressTablesTest.SELF, silent))
                .isInstanceOf(AddressTableException.class).hasMessageContaining("can't ask http://java-pricing:8080 who it is");
    }

    @Test
    void aConfirmedRoleCarriesItsInstanceAndALocalRoleIsAPartOfTheApp() {
        Bound remote = Bindings.bind(JAVA_21, JAVA_URL, CATALOGUE, StageAddressTablesTest.INSTANCES,
                StageAddressTablesTest.SELF, StageAddressTablesTest.DECLARED_META);
        assertThat(remote.instance().identity()).isEqualTo("java-pricing-v2.1");
        assertThat(remote.handle()).isEqualTo("http://java-pricing:8080");

        Address core = Address.parse("pkg:amorphic/pricing-calc@" + PricingCalcCore.VERSION + "?lang=java");
        Bound local = Bindings.bind(core, LOCAL, CATALOGUE, StageAddressTablesTest.INSTANCES, StageAddressTablesTest.SELF,
                (url, routed) -> { throw new AssertionError("a local role is not asked"); });
        assertThat(local.instance()).isEqualTo(new dev.amorphic.envelope.InstanceRef("demo-app-dev/pricing-calc", core, "demo-app-dev"));
        assertThat(local.handle()).isNull();
    }
}

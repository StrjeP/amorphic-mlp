package dev.amorphic.demo.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.amorphic.access.AccessException;
import dev.amorphic.access.AdapterBinding;
import dev.amorphic.access.Invoker;
import dev.amorphic.access.LocalBinding;
import dev.amorphic.access.RemoteBinding;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Instance;
import dev.amorphic.envelope.InstanceRef;
import dev.amorphic.inventory.Catalogue;
import dev.amorphic.inventory.Instances;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every stage's address table in amorphic-demo-env must load and resolve. */
class StageAddressTablesTest {

    static final Catalogue CATALOGUE = Catalogue.load(Path.of("../amorphic-demo-env/catalogue.yaml"));
    static final Instances INSTANCES = Instances.load(Path.of("../amorphic-demo-env/instances.yaml"));
    static final Instance SELF = Instance.deployed(Address.parse("pkg:amorphic/demo-app@0.1?lang=java"), "demo-app-dev", null);

    /** Stands in for each service's /meta: it answers as the instance declared for that handle and address. */
    static final MetaReader DECLARED_META = (url, routed) -> INSTANCES.at(url, routed)
            .map(d -> new InstanceRef(d.identity(), d.realises(), null))
            .orElseThrow(() -> new AccessException("nothing is declared at " + url + " for " + routed));

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "1b", "2", "3", "3b", "4"})
    void stageTableLoads(String stage) {
        AddressTable table = AddressTable.load(Path.of("../amorphic-demo-env/addresses/stage-" + stage + ".yaml"));
        AddressTable.Capability c = table.capability("pricing-calc");
        assertThat(Address.parse(c.champion().address()).name()).isEqualTo("pricing-calc");
        if (c.shadow() != null) Address.parse(c.shadow().address());
        if (c.canary() != null) assertThat(c.canary().weight()).isBetween(0, 100);
    }

    /** Every role in every stage gets a binding: Python through the adapter, Java remote speaking the envelope. */
    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "1b", "2", "3", "3b", "4"})
    void everyRoleBindsThroughTheCatalogue(String stage) {
        AddressTable.Capability c = AddressTable.load(Path.of("../amorphic-demo-env/addresses/stage-" + stage + ".yaml"))
                .capability("pricing-calc");
        for (AddressTable.Role role : new AddressTable.Role[] {c.champion(), c.shadow(), c.canary()}) {
            if (role == null) continue;
            Address address = Address.parse(role.address());
            Invoker invoker = Bindings.create(address, role.binding(), CATALOGUE);
            Class<?> expected = "local".equals(role.binding().type()) ? LocalBinding.class
                    : address.qualifier("lang").orElseThrow().equals("python") ? AdapterBinding.class : RemoteBinding.class;
            assertThat(invoker).as(stage + " " + address).isInstanceOf(expected);
        }
    }

    /** Every remote role in every stage is declared, so it passes the handshake when the declared instance answers. */
    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "1b", "2", "3", "3b", "4"})
    void everyRoleIsDeclared(String stage) {
        AddressTable.Capability c = AddressTable.load(Path.of("../amorphic-demo-env/addresses/stage-" + stage + ".yaml"))
                .capability("pricing-calc");
        for (AddressTable.Role role : new AddressTable.Role[] {c.champion(), c.shadow(), c.canary()}) {
            if (role == null) continue;
            Address address = Address.parse(role.address());
            Bound bound = Bindings.bind(address, role.binding(), CATALOGUE, INSTANCES, SELF, DECLARED_META);
            assertThat(bound.instance().realises()).as(stage + " " + address).isEqualTo(address);
            assertThat(bound.undeclared()).isFalse();
        }
    }

    @Test
    void theDemoDoesNotAcceptUndeclaredInstances() {
        assertThat(CATALOGUE.acceptsUndeclared()).isFalse();
        assertThat(INSTANCES.identity("demo-app-remote")).isPresent();
        assertThat(INSTANCES.identity("demo-app-foldin")).isPresent();
    }

    @Test
    void thePricingSchemasAreInTheCatalogue() {
        for (Address schema : new Address[] {PricingEnvelopes.REQUEST, PricingEnvelopes.REPLY}) {
            assertThat(CATALOGUE.schema(schema).chosen()).as(schema.toString())
                    .hasValueSatisfying(s -> assertThat(s.text()).contains("\"$id\": \"" + schema + "\""));
        }
    }

    static final Pattern ADDRESS = Pattern.compile("^\\s*-?\\s*address:\\s*(\\S+)\\s*$", Pattern.MULTILINE);

    /** Every address the demo writes, in the stage files and the catalogue, reads and prints back exactly. */
    @Test
    void everyAddressOnDiskPrintsBackExactly() throws IOException {
        int seen = 0;
        for (String file : new String[] {"addresses/stage-0.yaml", "addresses/stage-1.yaml", "addresses/stage-1b.yaml",
                "addresses/stage-2.yaml", "addresses/stage-3.yaml", "addresses/stage-3b.yaml", "addresses/stage-4.yaml",
                "catalogue.yaml"}) {
            Matcher m = ADDRESS.matcher(Files.readString(Path.of("../amorphic-demo-env/" + file)));
            while (m.find()) {
                assertThat(Address.parse(m.group(1))).as(file).hasToString(m.group(1));
                seen++;
            }
        }
        assertThat(seen).isGreaterThan(15);
    }
}

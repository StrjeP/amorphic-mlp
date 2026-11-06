package dev.amorphic.demo.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every stage's address table in amorphic-demo-env must load and resolve. */
class StageAddressTablesTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "1b", "2", "3", "3b", "4"})
    void stageTableLoads(String stage) {
        AddressTable table = AddressTable.load(Path.of("../amorphic-demo-env/addresses/stage-" + stage + ".yaml"));
        AddressTable.Capability c = table.capability("pricing-calc");
        assertThat(Address.parse(c.champion().address()).name()).isEqualTo("pricing-calc");
        if (c.shadow() != null) Address.parse(c.shadow().address());
        if (c.canary() != null) assertThat(c.canary().weight()).isBetween(0, 100);
    }
}

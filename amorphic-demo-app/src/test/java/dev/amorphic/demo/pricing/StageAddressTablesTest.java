package dev.amorphic.demo.pricing;

import dev.amorphic.addressing.Address;
import static org.assertj.core.api.Assertions.assertThat;

import dev.amorphic.addressing.Address;

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

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "1b", "2", "3", "3b", "4"})
    void stageTableLoads(String stage) {
        AddressTable table = AddressTable.load(Path.of("../amorphic-demo-env/addresses/stage-" + stage + ".yaml"));
        AddressTable.Capability c = table.capability("pricing-calc");
        assertThat(Address.parse(c.champion().address()).name()).isEqualTo("pricing-calc");
        if (c.shadow() != null) Address.parse(c.shadow().address());
        if (c.canary() != null) assertThat(c.canary().weight()).isBetween(0, 100);
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

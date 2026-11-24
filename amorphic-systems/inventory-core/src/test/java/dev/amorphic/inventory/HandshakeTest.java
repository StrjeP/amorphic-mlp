package dev.amorphic.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.InstanceRef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HandshakeTest {

    static final Address V1 = Address.parse("pkg:amorphic/greeter@1.0?lang=java");
    static final Address V2 = Address.parse("pkg:amorphic/greeter@2.0?lang=java");
    static final String URL = "http://greeter:8080";

    /** Two versions declared one after the other behind the same handle. */
    static final Instances DECLARED = new Instances(List.of(
            new Instances.Declared("greeter-1", V1, URL), new Instances.Declared("greeter-2", V2, URL)));

    @Test
    void confirmsWhatTheRoutingMeans() {
        InstanceRef two = new InstanceRef("greeter-2", V2, null);
        assertThat(Handshake.check(URL + "/", V2, two, DECLARED, false)).isEqualTo(new Handshake.Confirmed(two));
    }

    @Test
    void aLingeringOldVersionIsRefusedNamingBothSides() {
        assertThatThrownBy(() -> Handshake.check(URL, V2, new InstanceRef("greeter-1", V1, null), DECLARED, false))
                .isInstanceOf(Handshake.Refused.class)
                .hasMessage("routed to " + V2 + ", but " + URL + " is greeter-1, which realises " + V1);
    }

    @Test
    void anUndeclaredInstanceIsRefusedUnlessTheCatalogueAcceptsIt() {
        InstanceRef stranger = new InstanceRef("greeter-x", V2, null);
        assertThatThrownBy(() -> Handshake.check(URL, V2, stranger, DECLARED, false))
                .hasMessage(URL + " is greeter-x, which isn't declared (expected greeter-2), and the catalogue doesn't "
                        + "accept undeclared instances");
        assertThat(Handshake.check(URL, V2, stranger, DECLARED, true)).isEqualTo(new Handshake.Undeclared(stranger));
    }

    @Test
    void aDeclaredIdentityMustMatchItsDeclaration() {
        assertThatThrownBy(() -> Handshake.check(URL, V2, new InstanceRef("greeter-1", V2, null), DECLARED, false))
                .hasMessageContaining("declared as realising " + V1);
        assertThatThrownBy(() -> Handshake.check("http://elsewhere:9000", V2, new InstanceRef("greeter-2", V2, null),
                DECLARED, false)).hasMessageContaining("declared at " + URL);
    }

    @Test
    void loadsDeclarations(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("instances.yaml");
        Files.writeString(file, """
                instances:
                  - identity: greeter-1
                    realises: pkg:amorphic/greeter@1.0?lang=java
                    handle: http://greeter:8080/
                  - identity: host-1
                    realises: pkg:amorphic/greeter-host@0.1?lang=java
                """);
        Instances loaded = Instances.load(file);
        assertThat(loaded.at("http://greeter:8080", V1)).hasValueSatisfying(d -> assertThat(d.identity()).isEqualTo("greeter-1"));
        assertThat(loaded.identity("host-1")).hasValueSatisfying(d -> assertThat(d.handle()).isNull());

        Files.writeString(file, "instances:\n  - identity: a\n    realises: pkg:amorphic/a@1.0\n  - identity: a\n    realises: pkg:amorphic/a@1.0\n");
        assertThatThrownBy(() -> Instances.load(file)).hasMessageContaining("a is declared twice");
    }

    @Test
    void theCatalogueSaysWhetherUndeclaredInstancesAreAccepted(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("catalogue.yaml");
        Files.writeString(file, "capabilities: {}\n");
        assertThat(Catalogue.load(file).acceptsUndeclared()).isFalse();
        Files.writeString(file, "accept-undeclared: true\ncapabilities: {}\n");
        assertThat(Catalogue.load(file).acceptsUndeclared()).isTrue();
    }
}

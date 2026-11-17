package dev.amorphic.addressing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InstanceTest {

    static final Address GREETER = Address.parse("pkg:amorphic/greeter@1.0.1?lang=java");
    static final Address HOST_APP = Address.parse("pkg:amorphic/front-desk@0.3?lang=java");

    @Test
    void anArtefactIsIdentifiedByItsHash() {
        Instance jar = Instance.artefact(GREETER, "sha256:9eabf5b5");
        assertThat(jar.identity()).isEqualTo("sha256:9eabf5b5");
        assertThat(jar.realises()).isEqualTo(GREETER);
        assertThat(jar.hostInstance()).isEmpty();
    }

    @Test
    void aDeployedServiceKeepsItsIdentityThroughNewHandles() {
        Instance service = Instance.deployed(GREETER, "greeter, deployment 7", null);
        // A restart is a new handle on the same identity; nothing about the instance changes.
        Instance afterRestart = Instance.deployed(GREETER, "greeter, deployment 7", null);
        assertThat(afterRestart).isEqualTo(service);
        // A new artefact is deployed as a new instance.
        Instance next = Instance.deployed(Address.parse("pkg:amorphic/greeter@1.0.2?lang=java"), "greeter, deployment 8", null);
        assertThat(next).isNotEqualTo(service);
    }

    @Test
    void aPartIsNamedByItsHostAndItsName() {
        Instance app = Instance.deployed(HOST_APP, "front-desk, deployment 3", null);
        Instance part = Instance.part(app, "greeter", GREETER);
        assertThat(part.identity()).isEqualTo("front-desk, deployment 3/greeter");
        assertThat(part.hostInstance()).hasValue(app);
        // An instance points to what it realises; it is not on the ladder of logical addresses.
        assertThat(part.realises().rung()).isEqualTo(Rung.IMPLEMENTATION);
    }

    @Test
    void needsAnIdentityAndWhatItRealises() {
        assertThatThrownBy(() -> Instance.artefact(GREETER, " ")).isInstanceOf(AddressException.class);
        assertThatThrownBy(() -> Instance.artefact(null, "sha256:1")).isInstanceOf(NullPointerException.class);
    }
}

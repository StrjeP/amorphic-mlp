package dev.amorphic.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Resolution;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Schemas and access in the catalogue (spec 02), with neutral examples. */
class CatalogueSchemasTest {

    static final String ADAPTER = """
                    access:
                      via: adapter
                      adapter:
                        method: GET
                        path: /greet
                        query: { name: who }
                        reply: { message: text }
                        schema: pkg:schema/greeting-reply@1.0
            """;

    static final String CATALOGUE = """
            capabilities:
              greeter:
                kind: capability
                versions:
                  - address: pkg:amorphic/greeter@1.0?lang=python
                    locations: [remote]
                    service: { name: py-greeter, url: http://py-greeter:8000 }
            """ + ADAPTER + """
                  - address: pkg:amorphic/greeter@1.1?lang=java
                    locations: [remote, local]
                    access: { via: envelope }
            schemas:
              - address: pkg:schema/greeting-request@1.0
                file: schemas/greeting-request-1.0.json
              - address: pkg:schema/greeting-request@1.1
                file: schemas/greeting-request-1.1.json
            """;

    @TempDir
    Path dir;

    Catalogue load(String yaml) throws Exception {
        Files.createDirectories(dir.resolve("schemas"));
        Files.writeString(dir.resolve("schemas/greeting-request-1.0.json"), "{\"$id\":\"pkg:schema/greeting-request@1.0\"}");
        Files.writeString(dir.resolve("schemas/greeting-request-1.1.json"), "{\"$id\":\"pkg:schema/greeting-request@1.1\"}");
        Path file = dir.resolve("catalogue.yaml");
        Files.writeString(file, yaml);
        return Catalogue.load(file);
    }

    @Test
    void eachVersionSaysHowItIsReached() throws Exception {
        Catalogue c = load(CATALOGUE);
        var py = c.find("greeter", Address.parse("pkg:amorphic/greeter@1.0?lang=python")).orElseThrow();
        assertThat(py.access().viaAdapter()).isTrue();
        assertThat(py.access().adapter().query()).containsEntry("name", "who");
        assertThat(py.access().adapter().schema()).hasToString("pkg:schema/greeting-reply@1.0");
        var java = c.find("greeter", Address.parse("pkg:amorphic/greeter@1.1?lang=java")).orElseThrow();
        assertThat(java.access().viaAdapter()).isFalse();
    }

    @Test
    void schemasResolveByAddressLikeAnythingElse() throws Exception {
        Catalogue c = load(CATALOGUE);
        assertThat(c.schema(Address.parse("pkg:schema/greeting-request@1.*")).chosen().orElseThrow().text())
                .contains("greeting-request@1.1");
        assertThat(c.schema(Address.parse("pkg:schema/greeting-request@1.0")).chosen().orElseThrow().text())
                .contains("greeting-request@1.0");
        assertThat(c.schema(Address.parse("pkg:schema/greeting-reply@1.0"))).isInstanceOf(Resolution.NoMatch.class);
    }

    @Test
    void theFrameworksOwnSchemasResolveToo() throws Exception {
        Catalogue c = load(CATALOGUE);
        var causation = c.schema(Address.parse("pkg:schema/causation@1.0")).chosen().orElseThrow();
        assertThat(causation.isBuiltIn()).isTrue();
        assertThat(causation.text()).contains("causedBy");
        assertThat(c.schema(Address.parse("pkg:envelope/request@1.0")).chosen()).isPresent();
    }

    @Test
    void aMissingSchemaFileFailsTheLoad() {
        assertThatThrownBy(() -> load(CATALOGUE.replace("greeting-request-1.1.json", "nope.json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema pkg:schema/greeting-request@1.1 points to schemas/nope.json, which doesn't exist");
    }

    @Test
    void anUnknownOrIncompleteAccessFailsTheLoad() {
        assertThatThrownBy(() -> load(CATALOGUE.replace("via: envelope", "via: carrier-pigeon")))
                .hasMessageContaining("access via 'carrier-pigeon' is not known");
        assertThatThrownBy(() -> load(CATALOGUE.replace(ADAPTER, "        access: { via: adapter }\n")))
                .hasMessageContaining("access via adapter needs an 'adapter'");
    }
}

package dev.amorphic.envelope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.amorphic.addressing.Address;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvelopeTest {

    static final Address GREETING_REQUEST = Address.parse("pkg:schema/greeting-request@1.0");
    static final Address GREETING_REPLY = Address.parse("pkg:schema/greeting-reply@1.0");

    static final String SPEC_EXAMPLE = """
            {"id":"e-7f3a","purpose":"pkg:envelope/request@1.0","aspects":{\
            "payload":{"schema":"pkg:schema/greeting-request@1.0","body":{"name":"Ada"}},\
            "causation":{"schema":"pkg:schema/causation@1.0","body":{"causedBy":["r-0042"]}}}}""";

    @Test
    void readsAndWritesTheSpecExampleExactly() {
        Envelope e = EnvelopeJson.read(SPEC_EXAMPLE);
        assertThat(e.id()).isEqualTo("e-7f3a");
        assertThat(e.purpose()).isEqualTo(Envelope.REQUEST);
        assertThat(e.payload().schema()).isEqualTo(GREETING_REQUEST);
        assertThat(e.payload().body().path("name").asText()).isEqualTo("Ada");
        assertThat(e.causedBy()).containsExactly("r-0042");
        assertThat(EnvelopeJson.write(e)).isEqualTo(SPEC_EXAMPLE);
    }

    @Test
    void aReplyNamesTheRequestItAnswers() {
        Envelope request = Envelope.request(new Aspect(GREETING_REQUEST, Envelope.body().put("name", "Ada")), "r-1");
        Envelope reply = Envelope.reply(request, new Aspect(GREETING_REPLY, Envelope.body().put("text", "Hello, Ada")));
        assertThat(reply.purpose()).isEqualTo(Envelope.REPLY);
        assertThat(reply.causedBy()).containsExactly(request.id());
        assertThat(reply.isError()).isFalse();
        assertThat(EnvelopeJson.read(EnvelopeJson.write(reply))).isEqualTo(reply);
    }

    @Test
    void aFailureIsAReplyWhosePayloadIsAnError() {
        Envelope request = Envelope.request(new Aspect(GREETING_REQUEST, Envelope.body()), "r-1");
        Envelope failed = Envelope.error(request, "unknown name");
        assertThat(failed.purpose()).isEqualTo(Envelope.REPLY);
        assertThat(failed.isError()).isTrue();
        assertThat(failed.errorReason()).hasValue("unknown name");
        assertThat(failed.payload().schema()).isEqualTo(Envelope.ERROR_SCHEMA);
    }

    @Test
    void requestsAndRepliesNeedAPayloadAndACausation() {
        Aspect payload = new Aspect(GREETING_REQUEST, Envelope.body());
        assertThatThrownBy(() -> new Envelope("e-1", Envelope.REQUEST, Map.of("payload", payload)))
                .isInstanceOf(EnvelopeException.class)
                .hasMessage("envelope e-1 is a pkg:envelope/request@1.0, which needs a 'causation' aspect; it has [payload]");
        assertThatThrownBy(() -> EnvelopeJson.read("{\"id\":\"e-2\",\"purpose\":\"pkg:envelope/reply@1.0\"}"))
                .hasMessageContaining("needs a 'payload' aspect; it has none");
    }

    @Test
    void extraAspectsAreKeptAndCanBeIgnored() {
        String withTrace = SPEC_EXAMPLE.replace("}}}}", "}},\"trace\":{\"schema\":\"pkg:schema/trace@0.1\",\"body\":{\"span\":\"s-9\"}}}}");
        Envelope e = EnvelopeJson.read(withTrace);
        assertThat(e.aspects()).containsOnlyKeys("payload", "causation", "trace");
        assertThat(e.payload().body().path("name").asText()).isEqualTo("Ada");
        assertThat(EnvelopeJson.write(e)).isEqualTo(withTrace);
    }

    @Test
    void nothingElseMaySitAtTheTopLevel() {
        assertThatThrownBy(() -> EnvelopeJson.read(SPEC_EXAMPLE.replace("{\"id\"", "{\"user\":\"ada\",\"id\"")))
                .isInstanceOf(EnvelopeException.class)
                .hasMessageContaining("found 'user' (put it in an aspect)");
    }

    @Test
    void addressesAreReadAtTheEdge() {
        assertThatThrownBy(() -> EnvelopeJson.read(SPEC_EXAMPLE.replace("pkg:schema/greeting-request@1.0", "greeting")))
                .isInstanceOf(EnvelopeException.class)
                .hasMessageContaining("schema of aspect 'payload' is not readable: 'greeting' is not a valid address");
        assertThatThrownBy(() -> EnvelopeJson.read("not json")).hasMessageStartingWith("not a JSON envelope");
    }

    @Test
    void theFrameworksOwnSchemasShipInTheJar() {
        Schemas.BUILT_IN.keySet().forEach(a -> assertThat(Schemas.read(a)).contains("\"$id\": \"" + a + "\""));
    }
}

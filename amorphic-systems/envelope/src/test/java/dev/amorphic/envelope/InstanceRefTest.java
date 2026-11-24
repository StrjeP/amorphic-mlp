package dev.amorphic.envelope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Instance;
import org.junit.jupiter.api.Test;

class InstanceRefTest {

    static final Address GREETER = Address.parse("pkg:amorphic/greeter@1.0?lang=java");
    static final Address HOST = Address.parse("pkg:amorphic/greeter-host@0.1?lang=java");

    @Test
    void writesTheSpecTextFormAndReadsItBack() {
        InstanceRef ref = new InstanceRef("greeter-1", GREETER, null);
        assertThat(ref.json().toString())
                .isEqualTo("{\"identity\":\"greeter-1\",\"realises\":\"pkg:amorphic/greeter@1.0?lang=java\",\"host\":null}");
        assertThat(InstanceRef.read(ref.json())).isEqualTo(ref);
    }

    @Test
    void anInProcessPartNamesItsHostByIdentity() {
        Instance host = Instance.deployed(HOST, "host-7", null);
        InstanceRef part = InstanceRef.of(Instance.part(host, "greeter", GREETER));
        assertThat(part).isEqualTo(new InstanceRef("host-7/greeter", GREETER, "host-7"));
        assertThat(InstanceRef.read(part.json())).isEqualTo(part);
    }

    @Test
    void aReplyCanSayWhoAnswered() {
        Envelope request = Envelope.request(new Aspect(EnvelopeTest.GREETING_REQUEST, Envelope.body().put("name", "Ada")));
        Envelope reply = Envelope.reply(request, new Aspect(EnvelopeTest.GREETING_REPLY, Envelope.body()));
        assertThat(InstanceRef.from(reply)).isEmpty();

        InstanceRef me = new InstanceRef("greeter-1", GREETER, null);
        Envelope signed = EnvelopeJson.read(EnvelopeJson.write(reply.with(InstanceRef.ASPECT, me.aspect())));
        assertThat(signed.id()).isEqualTo(reply.id());
        assertThat(signed.aspect(InstanceRef.ASPECT).orElseThrow().schema()).isEqualTo(InstanceRef.SCHEMA);
        assertThat(InstanceRef.from(signed)).hasValue(me);
    }

    @Test
    void theSchemaIsBuiltIn() {
        assertThat(Schemas.read(InstanceRef.SCHEMA)).contains("\"$id\": \"pkg:schema/instance@1.0\"");
    }

    @Test
    void anInstanceNeedsAnIdentityAndAReadableAddress() {
        assertThatThrownBy(() -> new InstanceRef(" ", GREETER, null)).hasMessageContaining("identity");
        assertThatThrownBy(() -> InstanceRef.read(Envelope.body().put("identity", "x").put("realises", "not an address")))
                .hasMessageContaining("can't be read");
    }
}

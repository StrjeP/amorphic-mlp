package dev.amorphic.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.amorphic.envelope.Aspect;
import dev.amorphic.envelope.Envelope;
import dev.amorphic.envelope.EnvelopeJson;
import dev.amorphic.envelope.InstanceRef;
import dev.amorphic.pricing.PricingCalcCore;
import dev.amorphic.pricing.GoldenVectors;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({InvokeController.class, MetaController.class})
@Import(Self.class)
@TestPropertySource(properties = "amorphic.instance=java-pricing-test")
class InvokeControllerTest {

    @Autowired
    MockMvc mvc;

    static List<GoldenVectors.Case> golden() {
        return GoldenVectors.load();
    }

    static Envelope convert(String amount, String from, String to) {
        return Envelope.request(new Aspect(InvokeController.REQUEST,
                Envelope.body().put("amount", amount).put("from", from).put("to", to)), "r-1");
    }

    Envelope invoke(Envelope request) throws Exception {
        String body = mvc.perform(post("/invoke").contentType(MediaType.APPLICATION_JSON).content(EnvelopeJson.write(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return EnvelopeJson.read(body);
    }

    @ParameterizedTest
    @MethodSource("golden")
    void matchesGoldenVectorThroughTheEnvelope(GoldenVectors.Case c) throws Exception {
        Envelope request = convert(c.amount(), c.from(), c.to());
        Envelope reply = invoke(request);
        assertThat(reply.purpose()).isEqualTo(Envelope.REPLY);
        assertThat(reply.causedBy()).containsExactly(request.id());
        assertThat(reply.payload().schema()).isEqualTo(InvokeController.REPLY);
        assertThat(reply.payload().body().path("amount").asText()).isEqualTo(c.expected());
        assertThat(reply.payload().body().path("currency").asText()).isEqualTo(c.to());
    }

    @Test
    void anUnsupportedPairOrBadAmountIsAFailureReply() throws Exception {
        assertThat(invoke(convert("1.00", "USD", "JPY")).isError()).isTrue();
        assertThat(invoke(convert("abc", "GBP", "USD")).errorReason()).hasValue("invalid amount 'abc'");
    }

    @Test
    void aPayloadUnderAnotherSchemaIsAFailureReply() throws Exception {
        Envelope other = Envelope.request(new Aspect(dev.amorphic.addressing.Address.parse("pkg:schema/greeting-request@1.0"),
                Envelope.body()), "r-1");
        assertThat(invoke(other).errorReason()).hasValueSatisfying(r -> assertThat(r).contains("this service answers"));
    }

    @Test
    void anUnreadableEnvelopeIs400() throws Exception {
        mvc.perform(post("/invoke").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    /** Spec 03, section 6: every reply says who answered, and /meta says the same, from the code's own version. */
    @Test
    void repliesAndMetaSayWhoThisIs() throws Exception {
        InstanceRef me = new InstanceRef("java-pricing-test",
                dev.amorphic.addressing.Address.parse("pkg:amorphic/pricing-calc@" + PricingCalcCore.VERSION + "?lang=java"), null);
        assertThat(InstanceRef.from(invoke(convert("10.00", "GBP", "USD")))).hasValue(me);
        assertThat(InstanceRef.from(invoke(convert("1.00", "USD", "JPY")))).as("a failure reply too").hasValue(me);

        String meta = mvc.perform(get("/meta")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(InstanceRef.read(new com.fasterxml.jackson.databind.ObjectMapper().readTree(meta))).isEqualTo(me);
    }
}

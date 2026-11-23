package dev.amorphic.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.Aspect;
import dev.amorphic.envelope.Envelope;
import dev.amorphic.envelope.EnvelopeJson;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BindingsTest {

    static final Address GREETER = Address.parse("pkg:amorphic/greeter@1.0?lang=java");
    static final Address GREETING_REQUEST = Address.parse("pkg:schema/greeting-request@1.0");
    static final Address GREETING_REPLY = Address.parse("pkg:schema/greeting-reply@1.0");

    static Envelope greet(String name) {
        return Envelope.request(new Aspect(GREETING_REQUEST, Envelope.body().put("name", name)), "r-1");
    }

    /** What every greeter does, whichever way it is reached. */
    static Envelope greeter(Envelope request) {
        String name = request.payload().body().path("name").asText();
        if (name.isEmpty()) {
            return Envelope.error(request, "no name given");
        }
        return Envelope.reply(request, new Aspect(GREETING_REPLY, Envelope.body().put("text", "Hello, " + name)));
    }

    @Test
    void localHandsTheEnvelopeToAHandlerInProcess() {
        Reply reply = new LocalBinding(BindingsTest::greeter).invoke(GREETER, greet("Ada"));
        assertThat(reply.envelope().payload().body().path("text").asText()).isEqualTo("Hello, Ada");
        assertThat(reply.timing().total()).isPositive();
        assertThat(reply.timing().serviceTime()).isEmpty();
    }

    @Test
    void remotePostsTheEnvelopeAndReadsTheReplyEnvelope() throws Exception {
        try (Stub service = new Stub(seen -> new Stub.Answer(200,
                EnvelopeJson.write(greeter(EnvelopeJson.read(seen.body())))))) {
            Envelope request = greet("Ada");
            Reply reply = new RemoteBinding(service.url + "/").invoke(GREETER, request);

            assertThat(service.last.method()).isEqualTo("POST");
            assertThat(service.last.path()).isEqualTo("/invoke");
            assertThat(EnvelopeJson.read(service.last.body())).isEqualTo(request);
            assertThat(reply.envelope().causedBy()).containsExactly(request.id());
            assertThat(reply.envelope().payload().body().path("text").asText()).isEqualTo("Hello, Ada");
        }
    }

    @Test
    void aFailureReplyFromARemoteServiceIsStillAReply() throws Exception {
        try (Stub service = new Stub(seen -> new Stub.Answer(200,
                EnvelopeJson.write(greeter(EnvelopeJson.read(seen.body())))))) {
            Reply reply = new RemoteBinding(service.url).invoke(GREETER, greet(""));
            assertThat(reply.envelope().errorReason()).hasValue("no name given");
        }
    }

    @Test
    void aRemoteThatCantReadTheRequestIsAnAccessError() throws Exception {
        try (Stub service = new Stub(seen -> new Stub.Answer(400, "{\"error\":\"not an envelope\"}"))) {
            assertThatThrownBy(() -> new RemoteBinding(service.url).invoke(GREETER, greet("Ada")))
                    .isInstanceOf(AccessException.class)
                    .hasMessageContaining("answered 400 for pkg:amorphic/greeter@1.0?lang=java");
        }
    }

    static final AdapterSpec GREET_GET = new AdapterSpec("GET", "/greet", Map.of("name", "who"),
            Map.of("message", "text"), GREETING_REPLY);

    @Test
    void anAdapterMapsThePayloadOntoAPlainApiAndBack() throws Exception {
        try (Stub service = new Stub(seen -> new Stub.Answer(200, "{\"message\":\"Hello, Ada Lovelace\",\"server\":\"x\"}"))) {
            Envelope request = greet("Ada Lovelace");
            Reply reply = new AdapterBinding(service.url, GREET_GET).invoke(GREETER, request);

            assertThat(service.last.method()).isEqualTo("GET");
            assertThat(service.last.path()).isEqualTo("/greet");
            assertThat(service.last.query()).isEqualTo("who=Ada+Lovelace");
            Envelope r = reply.envelope();
            assertThat(r.purpose()).isEqualTo(Envelope.REPLY);
            assertThat(r.causedBy()).containsExactly(request.id());
            assertThat(r.payload().schema()).isEqualTo(GREETING_REPLY);
            assertThat(EnvelopeJson.tree(r).path("aspects").path("payload").path("body").toString())
                    .isEqualTo("{\"text\":\"Hello, Ada Lovelace\"}");
        }
    }

    @Test
    void anAdapterTimesTheServiceSoItsOwnCostShows() throws Exception {
        try (Stub service = new Stub(seen -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new Stub.Answer(200, "{\"message\":\"hi\"}");
        })) {
            Timing t = new AdapterBinding(service.url, GREET_GET).invoke(GREETER, greet("Ada")).timing();
            assertThat(t.serviceTime()).hasValueSatisfying(s -> assertThat(s.toMillis()).isGreaterThanOrEqualTo(30));
            assertThat(t.total()).isGreaterThanOrEqualTo(t.service());
            assertThat(t.adapterTime()).hasValueSatisfying(a -> assertThat(a.isNegative()).isFalse());
        }
    }

    @Test
    void anAdapterTurnsAClientErrorIntoAFailureReply() throws Exception {
        try (Stub service = new Stub(seen -> new Stub.Answer(400, "{\"error\":\"unknown name 'Zed'\"}"))) {
            Reply reply = new AdapterBinding(service.url, GREET_GET).invoke(GREETER, greet("Zed"));
            assertThat(reply.envelope().errorReason()).hasValue("unknown name 'Zed'");
        }
        try (Stub service = new Stub(seen -> new Stub.Answer(404, "not found"))) {
            assertThat(new AdapterBinding(service.url, GREET_GET).invoke(GREETER, greet("Zed")).envelope().errorReason())
                    .hasValue("status 404");
        }
    }

    @Test
    void anAdapterServerErrorOrMissingFieldIsAnAccessError() throws Exception {
        try (Stub service = new Stub(seen -> new Stub.Answer(500, "boom"))) {
            assertThatThrownBy(() -> new AdapterBinding(service.url, GREET_GET).invoke(GREETER, greet("Ada")))
                    .isInstanceOf(AccessException.class).hasMessageContaining("answered 500");
        }
        try (Stub service = new Stub(seen -> new Stub.Answer(200, "{\"other\":1}"))) {
            assertThatThrownBy(() -> new AdapterBinding(service.url, GREET_GET).invoke(GREETER, greet("Ada")))
                    .isInstanceOf(AccessException.class).hasMessageContaining("answered without 'message'");
        }
        assertThatThrownBy(() -> new AdapterBinding("http://127.0.0.1:1", GREET_GET)
                .invoke(GREETER, Envelope.request(new Aspect(GREETING_REQUEST, Envelope.body()))))
                .isInstanceOf(AccessException.class).hasMessageContaining("the payload has no 'name'");
    }

    @Test
    void anUnreachableServiceIsAnAccessError() {
        assertThatThrownBy(() -> new RemoteBinding("http://127.0.0.1:1").invoke(GREETER, greet("Ada")))
                .isInstanceOf(AccessException.class).hasMessageContaining("cannot reach http://127.0.0.1:1/invoke");
    }

    @Test
    void anAdapterSpecSaysWhatItCanDo() {
        assertThatThrownBy(() -> new AdapterSpec("POST", "/greet", Map.of(), Map.of("m", "t"), GREETING_REPLY))
                .hasMessageContaining("GET only");
        assertThatThrownBy(() -> new AdapterSpec("GET", "greet", Map.of(), Map.of("m", "t"), GREETING_REPLY))
                .hasMessageContaining("starting with /");
    }

    /** HTTP/1.1 only: an HTTP/2 upgrade header on every call makes uvicorn (the Python service) log a warning each time. */
    @Test
    void remoteAndAdapterNeverAskToUpgradeTheConnection() throws Exception {
        try (Stub service = new Stub(seen -> seen.path().equals("/invoke")
                ? new Stub.Answer(200, EnvelopeJson.write(greeter(EnvelopeJson.read(seen.body()))))
                : new Stub.Answer(200, "{\"message\": \"Hello, Ada\"}"))) {
            new RemoteBinding(service.url).invoke(GREETER, greet("Ada"));
            assertThat(service.last.upgrade()).as("remote").isNull();
            new AdapterBinding(service.url, GREET_GET).invoke(GREETER, greet("Ada"));
            assertThat(service.last.upgrade()).as("adapter").isNull();
        }
    }
}

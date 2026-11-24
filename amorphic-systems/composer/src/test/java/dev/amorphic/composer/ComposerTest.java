package dev.amorphic.composer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import dev.amorphic.access.Invoker;
import dev.amorphic.access.LocalBinding;
import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.Aspect;
import dev.amorphic.envelope.Envelope;
import dev.amorphic.envelope.InstanceRef;
import dev.amorphic.topology.TopologyLog;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Neutral examples: "loud greetings" lists some names, shouts each one that isn't already loud, and joins them.
 */
class ComposerTest {

    static final Address OUTCOME = Address.parse("pkg:outcome/loud-greetings@1.0");
    static final Address NAMES = Address.parse("pkg:amorphic/names@1.0");
    static final Address SHOUT = Address.parse("pkg:amorphic/shout@1.0?lang=java");
    static final Address SHOUT_BAD = Address.parse("pkg:amorphic/shout@1.1?lang=java");
    static final Address JOIN = Address.parse("pkg:amorphic/join@1.0");
    static final Address TEXT = Address.parse("pkg:schema/text@1.0");

    static final InstanceRef APP = new InstanceRef("app-1", Address.parse("pkg:amorphic/app@1.0"), null);

    static Recipe.Step step(String name, String capability, String each, Recipe.PassThrough p, Map<String, String> with) {
        return new Recipe.Step(name, Address.parse(capability), TEXT, each, p, with);
    }

    static final Recipe RECIPE = new Recipe(OUTCOME, TEXT, "Greets everyone, loudly.", List.of(
            step("names", "pkg:amorphic/names", null, null, Map.of("group", "input.group")),
            step("shout", "pkg:amorphic/shout", "names.people", new Recipe.PassThrough("item.loud", "input.loud"),
                    Map.of("text", "item.text")),
            step("join", "pkg:amorphic/join", null, null, Map.of("parts", "shout"))));

    static Envelope reply(Envelope request, JsonNode body) {
        return Envelope.reply(request, new Aspect(TEXT, body));
    }

    final AtomicInteger shouts = new AtomicInteger();
    final List<Envelope> seen = new ArrayList<>();

    final Invoker names = new LocalBinding(r -> {
        seen.add(r);
        var body = Envelope.body();
        var people = body.putArray("people");
        people.addObject().put("text", "ann").put("loud", false);
        people.addObject().put("text", "BOB").put("loud", true);
        people.addObject().put("text", "cy").put("loud", false);
        return reply(r, body);
    });

    Invoker shout(boolean wrong) {
        return new LocalBinding(r -> {
            seen.add(r);
            shouts.incrementAndGet();
            String text = r.payload().body().path("text").asText();
            if (text.equals("fail")) {
                return Envelope.error(r, "can't shout that");
            }
            return reply(r, Envelope.body().put("text", wrong ? text + "!" : text.toUpperCase()).put("loud", true));
        });
    }

    final Invoker join = new LocalBinding(r -> {
        seen.add(r);
        List<String> parts = new ArrayList<>();
        r.payload().body().path("parts").forEach(p -> parts.add(p.path("text").asText()));
        return reply(r, Envelope.body().put("text", String.join(" ", parts)));
    });

    static Router.Leg leg(String role, Address resolved, Invoker invoker, String identity) {
        return new Router.Leg(role, resolved, "local", null, new InstanceRef(identity, resolved, null), false, invoker);
    }

    final AtomicInteger begun = new AtomicInteger();
    final AtomicInteger routed = new AtomicInteger();

    Router router(boolean shadowed) {
        return () -> {
            begun.incrementAndGet();
            return new Router.Routing() {
                public long generation() {
                    return 4;
                }

                public Router.Route route(Recipe.Step step) {
                    routed.incrementAndGet();
                    return switch (step.name()) {
                        case "names" -> Router.Route.of(leg("champion", NAMES, names, "names-1"));
                        case "shout" -> new Router.Route(leg("champion", SHOUT, shout(false), "shout-1"),
                                shadowed ? leg("shadow", SHOUT_BAD, shout(true), "shout-2") : null);
                        case "join" -> Router.Route.of(leg("champion", JOIN, join, "join-1"));
                        default -> throw new CompositionException("no route for " + step.name());
                    };
                }
            };
        };
    }

    final TopologyLog log = TopologyLog.inMemory();

    Composer composer(boolean shadowed) {
        return new Composer(() -> List.of(RECIPE), router(shadowed), Runnable::run, log, APP, Clock.systemUTC());
    }

    static Envelope ask(boolean loud) {
        return Envelope.request(new Aspect(TEXT, Envelope.body().put("group", "team").put("loud", loud)), "caller-1");
    }

    @Test
    void runsTheStepsInOrderAndPassesThroughWhatIsAlreadyDone() {
        Run run = composer(false).run(Address.parse("pkg:outcome/loud-greetings"), ask(true));
        assertThat(run.failed()).isFalse();
        assertThat(run.result().path("text").asText()).isEqualTo("ANN BOB CY");
        assertThat(shouts).as("BOB is already loud, so no call").hasValue(2);
        assertThat(run.step("shout").callFor(1)).isEmpty();
    }

    @Test
    void everyCallIsCausedByTheOutcomeRequest() {
        Envelope request = ask(true);
        Run run = composer(false).run(OUTCOME, request);
        assertThat(seen).hasSize(4).allSatisfy(e -> assertThat(e.causedBy()).containsExactly(request.id()));
        assertThat(run.reply().causedBy()).containsExactly(request.id());
    }

    @Test
    void theRouteIsChosenOncePerPlanNotPerCall() {
        composer(false).run(OUTCOME, ask(true));
        assertThat(begun).hasValue(1);
        assertThat(routed).as("one per step, though shout is called twice").hasValue(3);
    }

    @Test
    void recordsOneTopologyForThePlanAndARecordForEveryCall() {
        Run run = composer(false).run(OUTCOME, ask(true));
        Address topology = run.completed().join().topology();
        assertThat(topology.toString()).startsWith("pkg:topology/loud-greetings?hash=");
        var t = log.topology(topology).orElseThrow();
        assertThat(t.roles()).extracting(r -> r.step() + " " + r.role() + " " + r.instance().identity())
                .containsExactly("names champion names-1", "shout champion shout-1", "join champion join-1");
        assertThat(t.roles().get(1).requested()).isEqualTo(Address.parse("pkg:amorphic/shout"));
        assertThat(t.roles().get(1).resolved()).isEqualTo(SHOUT);
        var calls = log.calls(topology);
        assertThat(calls).hasSize(5); // the outcome request, then names, shout twice, join
        assertThat(calls.get(0).envelope()).isEqualTo(run.request().id());
        assertThat(calls.get(0).causedBy()).containsExactly("caller-1");
        assertThat(calls).allSatisfy(c -> assertThat(c.generation()).isEqualTo(4));
    }

    @Test
    void aComparedStepGivesAShadowResultThatIsNeverTheAnswer() {
        Run run = composer(true).run(OUTCOME, ask(true));
        assertThat(run.result().path("text").asText()).isEqualTo("ANN BOB CY");
        Run.Completed done = run.completed().join();
        assertThat(done.shadowResult().path("text").asText()).isEqualTo("ann! BOB cy!");
        assertThat(done.shadowCalls()).as("join ran again on the shadow's answers").hasSize(1);
        var t = log.topology(done.topology()).orElseThrow();
        assertThat(t.roles()).extracting(r -> r.step() + " " + r.role())
                .containsExactly("names champion", "shout champion", "shout shadow", "join champion");
        assertThat(log.calls(done.topology())).hasSize(6);
    }

    @Test
    void withNothingComparedThereIsNoShadowResult() {
        Run.Completed done = composer(false).run(OUTCOME, ask(true)).completed().join();
        assertThat(done.compared()).isFalse();
        assertThat(done.shadowResult()).isNull();
    }

    @Test
    void aStepThatAnswersWithAnErrorEndsThePlanNamingIt() {
        Invoker failing = new LocalBinding(r -> {
            var body = Envelope.body();
            body.putArray("people").addObject().put("text", "fail").put("loud", false);
            return reply(r, body);
        });
        Router router = () -> new Router.Routing() {
            public long generation() {
                return 1;
            }

            public Router.Route route(Recipe.Step step) {
                return switch (step.name()) {
                    case "names" -> Router.Route.of(leg("champion", NAMES, failing, "names-1"));
                    case "shout" -> Router.Route.of(leg("champion", SHOUT, shout(false), "shout-1"));
                    default -> Router.Route.of(leg("champion", JOIN, join, "join-1"));
                };
            }
        };
        Run run = new Composer(() -> List.of(RECIPE), router, Runnable::run, log, APP, Clock.systemUTC())
                .run(OUTCOME, ask(true));
        assertThat(run.failed()).isTrue();
        assertThat(run.failure()).isEqualTo(new Run.Failure("shout", 0, "can't shout that"));
        assertThat(run.reply().errorReason()).hasValue("step 'shout', item 0: can't shout that");
    }

    @Test
    void aRouteToSomethingTheStepDidNotAskForIsRefused() {
        Router wrong = () -> new Router.Routing() {
            public long generation() {
                return 1;
            }

            public Router.Route route(Recipe.Step step) {
                return Router.Route.of(leg("champion", JOIN, join, "join-1"));
            }
        };
        assertThatThrownBy(() -> new Composer(() -> List.of(RECIPE), wrong, Runnable::run, null, APP, Clock.systemUTC())
                .run(OUTCOME, ask(true)))
                .isInstanceOf(CompositionException.class)
                .hasMessage("step 'names' asks for pkg:amorphic/names, but the routing gives pkg:amorphic/join@1.0");
    }

    @Test
    void anOutcomeWithNoRecipeIsRefused() {
        assertThatThrownBy(() -> composer(false).run(Address.parse("pkg:outcome/loud-greetings@2.0"), ask(true)))
                .isInstanceOf(CompositionException.class).hasMessageStartingWith("no recipe for pkg:outcome/loud-greetings@2.0");
        assertThatThrownBy(() -> composer(false).run(NAMES, ask(true)))
                .isInstanceOf(CompositionException.class).hasMessageContaining("isn't an outcome");
    }

    @Test
    void aRecipeThatCantRunFailsWhenItIsRead() {
        assertThatThrownBy(() -> new Recipe(OUTCOME, TEXT, null, List.of(
                step("a", "pkg:amorphic/names", null, null, Map.of("x", "later.y")),
                step("later", "pkg:amorphic/join", null, null, Map.of()))))
                .hasMessageContaining("'later.y' must start with input or an earlier step's name");
        assertThatThrownBy(() -> new Recipe(OUTCOME, TEXT, null, List.of(
                step("a", "pkg:amorphic/names", null, null, Map.of("x", "item.y")))))
                .hasMessageContaining("'item.y' must start with input or an earlier step's name");
        assertThatThrownBy(() -> new Recipe(OUTCOME, TEXT, null, List.of(
                step("a", "pkg:amorphic/names", "input.list", null, Map.of()))))
                .hasMessageContaining("can't fan out");
        assertThatThrownBy(() -> new Recipe(Address.parse("pkg:amorphic/greeter@1.0"), TEXT, null, RECIPE.steps()))
                .hasMessageContaining("pkg:outcome/<name>@<version>");
    }
}

package dev.amorphic.composer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.amorphic.access.Reply;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Preference;
import dev.amorphic.addressing.Resolution;
import dev.amorphic.addressing.Resolver;
import dev.amorphic.envelope.Aspect;
import dev.amorphic.envelope.Envelope;
import dev.amorphic.envelope.InstanceRef;
import dev.amorphic.topology.Topology;
import dev.amorphic.topology.TopologyLog;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Turns an outcome request into a plan and runs it (spec 04).
 *
 * <ol>
 *   <li>Finds the outcome's recipe, by best match among the recipes it is given (spec 01).</li>
 *   <li>Chooses every step's route once, from one snapshot of the routing (section 5).</li>
 *   <li>Runs the steps in order, one {@code invoke} per call, each caused by the outcome request (section 6). A
 *       compared leg is called in the background with the same request.</li>
 *   <li>Once every comparison is in, finishes the plan on the compared answers to give the shadow result (section
 *       8), and records the plan's topology and every call (section 7).</li>
 * </ol>
 *
 * The composer computes nothing itself: every step is an addressed call.
 */
public final class Composer {

    private final Supplier<List<Recipe>> recipes;
    private final Router router;
    private final Executor background;
    private final TopologyLog topologies;
    private final InstanceRef caller;
    private final Clock clock;

    /**
     * @param recipes    the recipes to choose from, read each time (the catalogue's)
     * @param background where comparisons run
     * @param topologies where each plan's topology and calls are recorded, or null to record none
     * @param caller     the instance asking: the caller in every topology
     */
    public Composer(Supplier<List<Recipe>> recipes, Router router, Executor background, TopologyLog topologies,
            InstanceRef caller, Clock clock) {
        this.recipes = recipes;
        this.router = router;
        this.background = background;
        this.topologies = topologies;
        this.caller = caller;
        this.clock = clock;
    }

    /** The recipe that {@code outcome} resolves to. */
    public Recipe recipe(Address outcome) {
        if (!Recipe.KIND.equals(outcome.kind())) {
            throw new CompositionException(outcome + " isn't an outcome (pkg:" + Recipe.KIND + "/<name>)");
        }
        Resolution<Recipe> r = Resolver.resolve(outcome, recipes.get(), Preference.HIGHEST_VERSION);
        if (r instanceof Resolution.NoMatch<Recipe> none) {
            throw new CompositionException("no recipe for " + outcome + ": " + none.reason());
        }
        return r.chosen().orElseThrow();
    }

    /**
     * Runs {@code outcome} for {@code request}, whose payload is the outcome's input.
     *
     * @throws CompositionException if no plan can be made, or a step can't reach what it is routed to
     */
    public Run run(Address outcome, Envelope request) {
        Recipe recipe = recipe(outcome);
        Router.Routing routing = router.begin();
        Map<String, Router.Route> routes = new LinkedHashMap<>();
        for (Recipe.Step step : recipe.steps()) {
            routes.put(step.name(), checked(step, routing.route(step)));
        }

        Map<String, JsonNode> outputs = new LinkedHashMap<>();
        Scope scope = new Scope(request.payload().body(), outputs, null);
        List<Run.StepRun> ran = new ArrayList<>();
        for (Recipe.Step step : recipe.steps()) {
            StepResult r = runStep(step, routes.get(step.name()), scope, request.id(), true);
            if (r.failure() != null) {
                Run.Failure f = r.failure();
                Envelope error = Envelope.error(request, "step '" + f.step() + "'"
                        + (f.item() >= 0 ? ", item " + f.item() : "") + ": " + f.reason());
                return new Run(request, recipe, routing.generation(), error, f, List.copyOf(ran),
                        CompletableFuture.completedFuture(new Run.Completed(null, null, null, List.of())));
            }
            ran.add(r.run());
            outputs.put(step.name(), r.run().output());
        }

        Run.StepRun last = ran.get(ran.size() - 1);
        Envelope reply = Envelope.reply(request, new Aspect(last.calls().get(0).reply().envelope().payload().schema(),
                last.output()));
        List<CompletableFuture<Run.Compared>> pending = ran.stream().flatMap(s -> s.calls().stream())
                .map(Run.Call::compared).filter(Objects::nonNull).toList();
        Run run = new Run(request, recipe, routing.generation(), reply, null, List.copyOf(ran), new CompletableFuture<>());
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .thenApply(v -> complete(run, routes))
                .whenComplete((c, e) -> {
                    if (e != null) {
                        run.completed().completeExceptionally(e);
                    } else {
                        run.completed().complete(c);
                    }
                });
        return run;
    }

    /** A route whose legs don't realise what the step asks for is refused: nothing falls back silently. */
    private static Router.Route checked(Recipe.Step step, Router.Route route) {
        for (Router.Leg leg : java.util.Arrays.asList(route.served(), route.compared())) {
            if (leg != null && !step.capability().matches(leg.resolved())) {
                throw new CompositionException("step '" + step.name() + "' asks for " + step.capability()
                        + ", but the routing gives " + leg.resolved());
            }
        }
        return route;
    }

    private record StepResult(Run.StepRun run, Run.Failure failure) {}

    private StepResult runStep(Recipe.Step step, Router.Route route, Scope scope, String causedBy, boolean compare) {
        List<Run.Call> calls = new ArrayList<>();
        if (!step.fansOut()) {
            Run.Call call = call(step, -1, route, scope, causedBy, compare);
            calls.add(call);
            Run.Failure f = failure(call);
            return f != null ? new StepResult(null, f)
                    : new StepResult(new Run.StepRun(step, route, body(call.reply()), null, calls), null);
        }
        JsonNode list = eval(step, scope, step.each());
        if (!list.isArray()) {
            throw new CompositionException("step '" + step.name() + "': '" + step.each() + "' isn't a list");
        }
        List<JsonNode> items = new ArrayList<>();
        list.forEach(items::add);
        ArrayNode output = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < items.size(); i++) {
            Scope s = scope.withItem(items.get(i));
            if (passesThrough(step, s)) {
                output.add(items.get(i));
                continue;
            }
            Run.Call call = call(step, i, route, s, causedBy, compare);
            calls.add(call);
            Run.Failure f = failure(call);
            if (f != null) {
                return new StepResult(null, f);
            }
            output.add(body(call.reply()));
        }
        return new StepResult(new Run.StepRun(step, route, output, List.copyOf(items), List.copyOf(calls)), null);
    }

    private static boolean passesThrough(Recipe.Step step, Scope s) {
        Recipe.PassThrough p = step.passThrough();
        return p != null && eval(step, s, p.when()).equals(eval(step, s, p.equals()));
    }

    private Run.Call call(Recipe.Step step, int item, Router.Route route, Scope scope, String causedBy,
            boolean compare) {
        ObjectNode payload = Envelope.body();
        step.with().forEach((field, expression) -> payload.set(field, eval(step, scope, expression)));
        Envelope request = Envelope.request(new Aspect(step.schema(), payload), causedBy);
        Router.Leg served = route.served();
        Reply reply;
        try {
            reply = served.invoker().invoke(served.resolved(), request);
        } catch (RuntimeException e) {
            throw new CompositionException("step '" + step.name() + "' can't reach " + served.resolved() + ": "
                    + e.getMessage(), e);
        }
        CompletableFuture<Run.Compared> compared = compare && route.compared() != null
                ? CompletableFuture.supplyAsync(() -> compare(route.compared(), request), background)
                : null;
        return new Run.Call(step, item, request, reply, answered(served, reply), compared);
    }

    private static Run.Compared compare(Router.Leg leg, Envelope request) {
        try {
            Reply r = leg.invoker().invoke(leg.resolved(), request);
            return new Run.Compared(r, answered(leg, r), r.envelope().errorReason().orElse(null));
        } catch (RuntimeException e) {
            // Nothing answered, so the topology names who the handshake found there.
            return new Run.Compared(null, leg.expected(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Who answered: what the reply says, where the service speaks the envelope; otherwise who was expected. */
    private static InstanceRef answered(Router.Leg leg, Reply reply) {
        return InstanceRef.from(reply.envelope()).orElse(leg.expected());
    }

    private static Run.Failure failure(Run.Call call) {
        return call.reply().envelope().errorReason()
                .map(reason -> new Run.Failure(call.step().name(), call.item(), reason)).orElse(null);
    }

    private static JsonNode body(Reply reply) {
        return reply.envelope().payload().body();
    }

    private static JsonNode eval(Recipe.Step step, Scope scope, String expression) {
        try {
            return scope.eval(expression);
        } catch (IllegalArgumentException e) {
            throw new CompositionException("step '" + step.name() + "': " + e.getMessage(), e);
        }
    }

    /** Every comparison is in: work out the shadow result, then record the topology and the calls. */
    private Run.Completed complete(Run run, Map<String, Router.Route> routes) {
        Map<String, JsonNode> shadow = new LinkedHashMap<>();
        Scope scope = new Scope(run.request().payload().body(), shadow, null);
        List<Run.Call> shadowCalls = new ArrayList<>();
        boolean active = false;
        String error = null;
        for (Run.StepRun s : run.steps()) {
            JsonNode out;
            if (s.route().compared() != null) {
                active = true;
                ShadowOutput so = shadowOutput(s);
                if (so.error() != null) {
                    error = so.error();
                    break;
                }
                out = so.output();
            } else if (active) {
                StepResult r;
                try {
                    r = runStep(s.step(), s.route(), scope, run.request().id(), false);
                } catch (CompositionException e) {
                    error = e.getMessage();
                    break;
                }
                if (r.failure() != null) {
                    error = "step '" + r.failure().step() + "': " + r.failure().reason();
                    break;
                }
                shadowCalls.addAll(r.run().calls());
                out = r.run().output();
            } else {
                out = s.output();
            }
            shadow.put(s.step().name(), out);
        }
        JsonNode result = active && error == null ? shadow.get(run.recipe().steps().get(run.recipe().steps().size() - 1).name()) : null;
        Address topology = record(run, shadowCalls);
        return new Run.Completed(topology, result, active ? error : null, List.copyOf(shadowCalls));
    }

    private record ShadowOutput(JsonNode output, String error) {}

    private static ShadowOutput shadowOutput(Run.StepRun s) {
        if (!s.step().fansOut()) {
            Run.Compared c = s.calls().get(0).compared().join();
            return c.ok() ? new ShadowOutput(body(c.reply()), null) : new ShadowOutput(null, "step '" + s.step().name() + "': " + c.error());
        }
        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < s.items().size(); i++) {
            var call = s.callFor(i);
            if (call.isEmpty()) {
                out.add(s.items().get(i));
                continue;
            }
            Run.Compared c = call.get().compared().join();
            if (!c.ok()) {
                return new ShadowOutput(null, "step '" + s.step().name() + "', item " + i + ": " + c.error());
            }
            out.add(body(c.reply()));
        }
        return new ShadowOutput(out, null);
    }

    /** One topology for the whole plan, and one record for the outcome request and for every call. */
    private Address record(Run run, List<Run.Call> shadowCalls) {
        if (topologies == null) {
            return null;
        }
        List<Topology.Role> roles = new ArrayList<>();
        for (Run.StepRun s : run.steps()) {
            List<Run.Call> calls = new ArrayList<>(s.calls());
            shadowCalls.stream().filter(c -> c.step().name().equals(s.step().name())).forEach(calls::add);
            if (calls.isEmpty()) {
                continue; // every item passed through: this step used nothing
            }
            Set<InstanceRef> served = new LinkedHashSet<>();
            calls.forEach(c -> served.add(c.answered()));
            served.forEach(i -> roles.add(role(s.step(), s.route().served(), i)));
            if (s.route().compared() != null) {
                Set<InstanceRef> compared = new LinkedHashSet<>();
                s.calls().forEach(c -> compared.add(c.compared().join().answered()));
                compared.forEach(i -> roles.add(role(s.step(), s.route().compared(), i)));
            }
        }
        if (roles.isEmpty()) {
            return null;
        }
        Topology topology = new Topology(run.recipe().name(), caller, roles);
        var now = clock.instant();
        Address address = topologies.record(topology, run.request().id(), run.request().causedBy(), run.generation(), now);
        List<Run.Call> all = new ArrayList<>();
        run.steps().forEach(s -> all.addAll(s.calls()));
        all.addAll(shadowCalls);
        for (Run.Call c : all) {
            topologies.record(topology, c.request().id(), c.request().causedBy(), run.generation(), now);
        }
        return address;
    }

    private static Topology.Role role(Recipe.Step step, Router.Leg leg, InstanceRef answered) {
        return new Topology.Role(step.name(), leg.role(), step.capability(), leg.resolved(), leg.binding(),
                leg.handle(), answered, leg.undeclared());
    }
}

package dev.amorphic.composer;

import com.fasterxml.jackson.databind.JsonNode;
import dev.amorphic.access.Reply;
import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.Envelope;
import dev.amorphic.envelope.InstanceRef;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * One plan, run (spec 04, sections 5 and 6): the outcome request, the reply, and every step with its calls.
 * {@code completed} finishes once every comparison is in, the shadow result (if any) is worked out and the topology
 * is recorded. The caller gets its answer without waiting for it.
 *
 * @param failure set when a step answered with an error; {@code reply} is then an error naming it
 */
public record Run(Envelope request, Recipe recipe, long generation, Envelope reply, Failure failure,
        List<StepRun> steps, CompletableFuture<Completed> completed) {

    public boolean failed() {
        return failure != null;
    }

    /** The outcome's result: the last step's output. */
    public JsonNode result() {
        return reply.payload().body();
    }

    public StepRun step(String name) {
        return steps.stream().filter(s -> s.step().name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no step '" + name + "' ran"));
    }

    /**
     * One step as it ran. For a fan-out, {@code items} are what it ran over and {@code output} is the list of
     * results in the same order; a passed-through item has no call.
     */
    public record StepRun(Recipe.Step step, Router.Route route, JsonNode output, List<JsonNode> items,
            List<Call> calls) {

        /** The call for item {@code i} of a fan-out, if one was made. */
        public java.util.Optional<Call> callFor(int i) {
            return calls.stream().filter(c -> c.item() == i).findFirst();
        }
    }

    /**
     * One call a step made. {@code item} is its index in a fan-out, or -1. {@code answered} is who served it: what
     * the reply says, or who the handshake found. {@code compared} is null when the route has no comparison.
     */
    public record Call(Recipe.Step step, int item, Envelope request, Reply reply, InstanceRef answered,
            CompletableFuture<Compared> compared) {}

    /** The comparison leg's answer: a reply, or the error that stopped it. {@code answered} is who it was. */
    public record Compared(Reply reply, InstanceRef answered, String error) {

        public boolean ok() {
            return error == null;
        }
    }

    /**
     * After every comparison is in. {@code topology} is the plan's topology, or null when none is recorded.
     * {@code shadowResult} is what the outcome would have been on the compared legs' answers (spec 04, section 8),
     * or null when nothing was compared; {@code shadowError} says why there is none, if a comparison failed.
     * {@code shadowCalls} are the later steps run again on those answers.
     */
    public record Completed(Address topology, JsonNode shadowResult, String shadowError, List<Call> shadowCalls) {

        public boolean compared() {
            return shadowResult != null || shadowError != null;
        }
    }

    /** The step that answered with an error, the item (or -1), and the reason it gave. */
    public record Failure(String step, int item, String reason) {}
}

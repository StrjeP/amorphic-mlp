package dev.amorphic.composer;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Candidate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How to reach an outcome (spec 04, section 4): an ordered list of steps. The outcome's result is the last step's
 * output. Pass 1 has one recipe per outcome version, kept in the catalogue beside the outcome.
 *
 * @param address     the outcome, under its own kind: {@code pkg:outcome/<name>@<version>}
 * @param input       the schema of the outcome's input
 * @param description what the outcome is, in plain words
 */
public record Recipe(Address address, Address input, String description, List<Step> steps) implements Candidate {

    public static final String KIND = "outcome";

    /**
     * One step: a capability address (any rung, resolved when the plan is made), the schema of the payload it is
     * sent, and how that payload is built ({@code with}: payload field to expression, see {@link Scope}).
     *
     * <p>With {@code each}, the step runs once for each item of a list, and its output is the list of results in
     * the same order. With {@code passThrough} as well, items meeting its condition are already done: they go on
     * unchanged and no call is made for them.
     */
    public record Step(String name, Address capability, Address schema, String each,
            @JsonProperty("pass-through") PassThrough passThrough, Map<String, String> with) {

        public Step {
            with = with == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(with));
        }

        public boolean fansOut() {
            return each != null;
        }
    }

    /** Items whose {@code when} equals {@code equals} need no call (both are expressions, see {@link Scope}). */
    public record PassThrough(String when, String equals) {}

    public Recipe {
        if (address == null || !KIND.equals(address.kind()) || address.version().isEmpty()) {
            throw new IllegalArgumentException("a recipe is for an outcome at an exact version, as pkg:" + KIND
                    + "/<name>@<version>; got " + address);
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("the recipe for " + address + " has no steps");
        }
        steps = List.copyOf(steps);
        Set<String> earlier = new HashSet<>();
        for (Step s : steps) {
            String where = "the recipe for " + address + ", step '" + s.name() + "'";
            if (s.name() == null || s.name().isBlank() || s.name().contains(".")) {
                throw new IllegalArgumentException("the recipe for " + address + " has a step without a plain name");
            }
            if (Scope.RESERVED.contains(s.name()) || !earlier.add(s.name())) {
                throw new IllegalArgumentException(where + ": the name is reserved or used twice");
            }
            if (s.capability() == null || s.schema() == null) {
                throw new IllegalArgumentException(where + ": needs a capability and a schema");
            }
            if (s.passThrough() != null && !s.fansOut()) {
                throw new IllegalArgumentException(where + ": pass-through needs 'each'");
            }
            Set<String> known = new HashSet<>(earlier);
            known.remove(s.name());
            if (s.fansOut()) {
                Scope.check(s.each(), known, false, where);
            }
            if (s.passThrough() != null) {
                Scope.check(s.passThrough().when(), known, true, where);
                Scope.check(s.passThrough().equals(), known, true, where);
            }
            s.with().values().forEach(e -> Scope.check(e, known, s.fansOut(), where));
        }
        if (steps.get(steps.size() - 1).fansOut()) {
            throw new IllegalArgumentException("the recipe for " + address
                    + ": the last step gives the result, so it can't fan out (add a step that combines them)");
        }
    }

    public String name() {
        return address.name();
    }

    /** The step called {@code name}. */
    public Step step(String name) {
        return steps.stream().filter(s -> s.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(address + " has no step '" + name + "'"));
    }

    /** The order of the steps by name, for messages. */
    public Map<String, Integer> order() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            m.put(steps.get(i).name(), i);
        }
        return m;
    }
}

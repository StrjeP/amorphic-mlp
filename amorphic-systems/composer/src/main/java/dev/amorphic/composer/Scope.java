package dev.amorphic.composer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.Map;
import java.util.Set;

/**
 * What a step's expressions can see. An expression is a dotted path that starts with {@code input} (the outcome's
 * input), {@code item} (the current item, in a fan-out step) or the name of an earlier step (its output), for
 * example {@code input.currency}, {@code item.amount} or {@code positions.positions}. Nothing else: no operators,
 * no literals.
 */
record Scope(JsonNode input, Map<String, JsonNode> outputs, JsonNode item) {

    static final String INPUT = "input";
    static final String ITEM = "item";
    static final Set<String> RESERVED = Set.of(INPUT, ITEM);

    Scope withItem(JsonNode i) {
        return new Scope(input, outputs, i);
    }

    /** The value at {@code expression}. A path that isn't there is an error naming it. */
    JsonNode eval(String expression) {
        String[] parts = expression.split("\\.");
        JsonNode node = switch (parts[0]) {
            case INPUT -> input;
            case ITEM -> item;
            default -> outputs.get(parts[0]);
        };
        if (node == null) {
            throw new IllegalArgumentException("'" + expression + "': nothing called '" + parts[0] + "' here");
        }
        for (int i = 1; i < parts.length; i++) {
            node = node.path(parts[i]);
        }
        if (node instanceof MissingNode) {
            throw new IllegalArgumentException("'" + expression + "' isn't in what the step was given");
        }
        return node;
    }

    /** Checks an expression when a recipe is read, so a bad recipe fails the load, not a call. */
    static void check(String expression, Set<String> earlierSteps, boolean itemAllowed, String where) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException(where + ": an empty expression");
        }
        String head = expression.split("\\.")[0];
        boolean ok = head.equals(INPUT) || (itemAllowed && head.equals(ITEM)) || earlierSteps.contains(head);
        if (!ok) {
            throw new IllegalArgumentException(where + ": '" + expression + "' must start with input"
                    + (itemAllowed ? ", item" : "") + " or an earlier step's name");
        }
    }
}

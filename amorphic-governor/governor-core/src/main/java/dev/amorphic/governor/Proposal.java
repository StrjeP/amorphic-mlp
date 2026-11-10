package dev.amorphic.governor;

import dev.amorphic.inventory.EvidenceView;
import dev.amorphic.inventory.RoutingView;
import java.time.Instant;
import java.util.List;

/**
 * A proposed address table and everything behind it: the rule that fired, why, and the evidence.
 *
 * @param basedOnGeneration the live table generation the proposal was made from; approval fails if it has changed
 * @param gate              how the rule's evidence gate was met, in words (null if the rule has no gate)
 * @param fromTable         the live table, rendered in the stage-file layout
 * @param toTable           the proposed table, in the same layout
 * @param needsRebuild      true if the app must be rebuilt to honour it (folding a module in)
 */
public record Proposal(
        String id,
        Instant createdAt,
        long basedOnGeneration,
        String capability,
        String rule,
        String why,
        String gate,
        EvidenceView evidence,
        String fromTable,
        String toTable,
        List<RoutingView.Role> toRoles,
        boolean needsRebuild) {

    public Proposal {
        toRoles = List.copyOf(toRoles);
    }
}

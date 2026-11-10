package dev.amorphic.governor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The governor, seen from its callers. Presumed remote: every call has a deadline and returns a
 * value; failures are results, not exceptions. The same interface is served in-process or by a
 * separate governor service. It proposes; a human approves. It never applies anything on its own.
 */
public interface Governor {

    /** Looks at the system now and proposes the next address table, if a rule says so. */
    ProposeResult propose(Duration timeout);

    /** Applies a proposal a human has approved, if the system hasn't moved on since. */
    ApproveResult approve(String proposalId, Duration timeout);

    Optional<Proposal> proposal(String proposalId);

    sealed interface ProposeResult permits Proposed, Waiting, Holding, NoChange, Unavailable {}

    /** A proposal, waiting for a human to approve it. */
    record Proposed(Proposal proposal) implements ProposeResult {}

    /** Not proposing yet: the inventory isn't ready, or a rule's evidence gate isn't met. */
    record Waiting(List<String> reasons) implements ProposeResult {}

    /** A rule says hold: nothing should move, and why. */
    record Holding(String rule, String why, List<String> details) implements ProposeResult {}

    /** No rule applies to the current situation. */
    record NoChange(String reason) implements ProposeResult {}

    /** The governor couldn't look at the system (inventory, catalogue or rules unavailable). */
    record Unavailable(String reason) implements ProposeResult {}

    sealed interface ApproveResult permits Applied, NeedsRebuild, Rejected {}

    /** Applied without a restart: the address table is now at {@code generation}. */
    record Applied(long generation, Proposal proposal) implements ApproveResult {}

    /** Can't be applied to the running app (it needs code the app wasn't built with); {@code how} says what to do. */
    record NeedsRebuild(Proposal proposal, String how) implements ApproveResult {}

    /** Not applied, and why (unknown proposal, the table moved on, or the app refused it). */
    record Rejected(String reason) implements ApproveResult {}
}

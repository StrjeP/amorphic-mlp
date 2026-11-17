package dev.amorphic.addressing;

import java.util.List;
import java.util.Optional;

/** What the {@link Resolver} found: one candidate, or no match with the reason. */
public sealed interface Resolution<C extends Candidate> {

    /** The chosen candidate. Warnings say anything the caller should know, e.g. that it is withdrawn. */
    record Resolved<C extends Candidate>(C candidate, List<String> warnings) implements Resolution<C> {
        public Resolved {
            warnings = List.copyOf(warnings);
        }
    }

    /** Nothing matched. {@code reason} names what was asked for and what is available. */
    record NoMatch<C extends Candidate>(Address requested, String reason) implements Resolution<C> {}

    default Optional<C> chosen() {
        return this instanceof Resolved<C> r ? Optional.of(r.candidate()) : Optional.empty();
    }
}

package dev.amorphic.addressing;

/** Something the {@link Resolver} can choose: a catalogued version, for example. */
public interface Candidate {

    /** Its address. The resolver only chooses candidates with a version. */
    Address address();

    /** Withdrawn by a human: skipped by a floating request, resolved with a warning by a pinned one. */
    default boolean isWithdrawn() {
        return false;
    }

    /** Not built yet: never resolved. */
    default boolean isPlanned() {
        return false;
    }
}

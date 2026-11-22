package dev.amorphic.addressing;

/**
 * How the caller wants a choice made. It belongs to the caller's request, not the address (spec 01, section 7).
 * There is one for now; nearest, cheapest and quickest come later.
 */
public enum Preference {
    /** The highest version that satisfies the request. */
    HIGHEST_VERSION
}

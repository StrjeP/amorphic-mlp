package dev.amorphic.addressing;

/**
 * The ladder of logical addresses (spec 01, section 3), from broadest to narrowest. Below implementation
 * everything is an {@link Instance}: a built artefact, a running service, or a part inside a host.
 */
public enum Rung {
    /** The capability alone, e.g. {@code pkg:amorphic/greeter}. */
    CAPABILITY,
    /** A wildcard range of versions, e.g. {@code pkg:amorphic/greeter@1.*}. */
    RANGE,
    /** One version, e.g. {@code pkg:amorphic/greeter@1.0.1}. */
    VERSION,
    /** A version narrowed by qualifiers, e.g. {@code pkg:amorphic/greeter@1.0.1?lang=java}. */
    IMPLEMENTATION
}

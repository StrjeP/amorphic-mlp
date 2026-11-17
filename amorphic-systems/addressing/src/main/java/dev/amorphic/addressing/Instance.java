package dev.amorphic.addressing;

import java.util.Objects;
import java.util.Optional;

/**
 * A manifestation: one specific thing that exists, "it is the" (spec 01, section 8). It has its own identity,
 * points to the logical {@link Address} it realises, and may run inside a host, which is an instance too.
 *
 * <p>The identity is stamped on when the instance is made and kept through new handles, like a car's VIN
 * through new number plates. Where it can be reached now (a binding) is a handle, kept beside it.
 *
 * <p>Slice 1 only names instances. A text form comes when something records what ran.
 */
public record Instance(String identity, Address realises, Instance host) {

    public Instance {
        if (identity == null || identity.isBlank()) {
            throw new AddressException("an instance needs an identity");
        }
        Objects.requireNonNull(realises, "an instance must say which address it realises");
    }

    /** A built artefact, such as a jar or an image: its identity is its content hash. */
    public static Instance artefact(Address realises, String hash) {
        return new Instance(hash, realises, null);
    }

    /**
     * A running service. Its identity is assigned when it is deployed: a restart keeps it, while deploying
     * a new artefact makes a new instance.
     */
    public static Instance deployed(Address realises, String deploymentId, Instance host) {
        return new Instance(deploymentId, realises, host);
    }

    /** A part inside a host, such as an in-process module: the host's identity plus the part's name. */
    public static Instance part(Instance host, String partName, Address realises) {
        Objects.requireNonNull(host, "a part needs a host");
        if (partName == null || partName.isBlank()) {
            throw new AddressException("a part needs a name");
        }
        return new Instance(host.identity() + "/" + partName, realises, host);
    }

    public Optional<Instance> hostInstance() {
        return Optional.ofNullable(host);
    }
}

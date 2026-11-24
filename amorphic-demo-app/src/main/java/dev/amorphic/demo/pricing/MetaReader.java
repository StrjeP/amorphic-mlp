package dev.amorphic.demo.pricing;

import dev.amorphic.access.Meta;
import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.InstanceRef;

/**
 * Asks the service at a handle who it is. The real one is {@link Meta#read}, {@code GET {url}/meta}; it ignores the
 * routed address, which a test stand-in can use to answer as the declared instance.
 */
@FunctionalInterface
public interface MetaReader {

    InstanceRef read(String url, Address routed);

    MetaReader HTTP = (url, routed) -> Meta.read(url);
}

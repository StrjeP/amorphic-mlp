package dev.amorphic.inventory;

import java.time.Duration;

/**
 * Where the inventory's observations come from. In-process they read the app directly; as a
 * separate service they call the app's /admin/address-table and /evidence ({@link HttpSources}).
 */
public final class Sources {

    private Sources() {}

    @FunctionalInterface
    public interface Routing {
        RoutingView current() throws Exception;
    }

    @FunctionalInterface
    public interface Evidence {
        EvidenceView current() throws Exception;
    }

    /** Checks whether a remote binding's service is answering. Must not take longer than {@code timeout}. */
    @FunctionalInterface
    public interface Health {
        HealthCheck check(String url, Duration timeout);
    }
}

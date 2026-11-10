package dev.amorphic.demo;

import dev.amorphic.demo.pricing.AddressTable;
import dev.amorphic.governor.Governor;
import dev.amorphic.governor.Proposal;
import dev.amorphic.governor.RemoteGovernor;
import dev.amorphic.inventory.Tooling;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The {@link Governor} the app's call sites use. Each call goes where the live address table's
 * {@code governor} entry says: {@code local} (or no entry) runs it in this app, {@code remote}
 * calls the governor service at the url.
 *
 * <p>Proposals live in the governor that made them. Moving the governor applies a new table, so
 * any pending proposal is stale anyway and would be refused.
 */
final class RoutedGovernor implements Governor {

    private final Supplier<AddressTable> table;
    private final Governor local;
    private final Map<String, RemoteGovernor> remotes = new ConcurrentHashMap<>();

    RoutedGovernor(Supplier<AddressTable> table, Governor local) {
        this.table = table;
        this.local = local;
    }

    private Governor target() {
        AddressTable.Binding b = table.get().tooling(Tooling.GOVERNOR);
        if (b == null || "local".equals(b.type())) {
            return local;
        }
        return remotes.computeIfAbsent(b.url(), RemoteGovernor::new);
    }

    @Override
    public ProposeResult propose(Duration timeout) {
        return target().propose(timeout);
    }

    @Override
    public ApproveResult approve(String proposalId, Duration timeout) {
        return target().approve(proposalId, timeout);
    }

    @Override
    public Optional<Proposal> proposal(String proposalId) {
        return target().proposal(proposalId);
    }
}

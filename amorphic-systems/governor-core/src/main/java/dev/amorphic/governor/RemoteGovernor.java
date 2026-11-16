package dev.amorphic.governor;

import dev.amorphic.inventory.Http;
import dev.amorphic.inventory.Json;
import java.time.Duration;
import java.util.Optional;

/**
 * The governor as a client of a separate governor service ({@code /governor/proposals}). The
 * deadline is handed on, and failures come back as results, never exceptions.
 */
public final class RemoteGovernor implements Governor {

    private static final Duration LOOKUP = Duration.ofSeconds(2);

    private final String url;

    public RemoteGovernor(String url) {
        this.url = Http.base(url);
    }

    public String url() {
        return url;
    }

    @Override
    public ProposeResult propose(Duration timeout) {
        try {
            Http.Response r = Http.post(url + "/governor/proposals?timeoutMs=" + Http.budgetMillis(timeout), "",
                    "application/json", timeout);
            return GovernorHttp.readPropose(r.body());
        } catch (Exception e) {
            return new Unavailable("governor at " + url + ": " + Http.why(e, timeout));
        }
    }

    @Override
    public ApproveResult approve(String proposalId, Duration timeout) {
        try {
            Http.Response r = Http.post(url + "/governor/proposals/" + proposalId + "/approve?timeoutMs="
                    + Http.budgetMillis(timeout), "", "application/json", timeout);
            return GovernorHttp.readApprove(r.body());
        } catch (Exception e) {
            return new Rejected("governor at " + url + ": " + Http.why(e, timeout));
        }
    }

    @Override
    public Optional<Proposal> proposal(String proposalId) {
        try {
            Http.Response r = Http.get(url + "/governor/proposals/" + proposalId, LOOKUP);
            return r.status() == 200 ? Optional.of(GovernorHttp.proposal(Json.MAPPER.readTree(r.body())))
                    : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

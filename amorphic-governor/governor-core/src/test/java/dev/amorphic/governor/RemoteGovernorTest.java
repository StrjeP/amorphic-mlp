package dev.amorphic.governor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.amorphic.inventory.EvidenceView;
import dev.amorphic.inventory.Json;
import dev.amorphic.inventory.RoutingView;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * A governor service serves {@link GovernorHttp}'s shapes, and {@link RemoteGovernor} reads them
 * back: every result means the same thing local or remote.
 */
class RemoteGovernorTest {

    static final Duration T = Duration.ofSeconds(2);

    static final Proposal P = new Proposal("g3-shadow-to-canary", Instant.parse("2026-11-10T12:00:00Z"), 3,
            "pricing-calc", "shadow-to-canary", "Java has matched", "1200 consecutive matches",
            new EvidenceView(3, List.of(new EvidenceView.Served("a", "remote", 1200)),
                    List.of(new EvidenceView.Comparison("a", "b", 1200, 1200, 0, 1200))),
            "capabilities:\n  from\n", "capabilities:\n  to\n",
            List.of(new RoutingView.Role("pricing-calc", "canary", "b", "remote", "http://java:8080", 10)), false);

    /** Serves one reply the way a governor service (or the demo app) would. */
    static Stub serving(Function<Stub.Request, GovernorHttp.Reply> reply) throws Exception {
        return new Stub(req -> {
            GovernorHttp.Reply r = reply.apply(req);
            try {
                return new Stub.Reply(r.status(), Json.MAPPER.writeValueAsString(r.body()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void everyProposeResultSurvivesTheTrip() throws Exception {
        List<Governor.ProposeResult> results = List.of(
                new Governor.Proposed(P),
                new Governor.Waiting(List.of("only 5 evidence records")),
                new Governor.Holding("hold-on-mismatch", "Any mismatch holds", List.of("a vs b: 2 mismatches")),
                new Governor.NoChange("folded in"),
                new Governor.Unavailable("inventory unavailable: x"));
        for (Governor.ProposeResult result : results) {
            try (Stub s = serving(req -> GovernorHttp.propose(result))) {
                assertThat(new RemoteGovernor(s.url).propose(T)).isEqualTo(result);
                assertThat(s.last.path()).isEqualTo("/governor/proposals");
                assertThat(s.last.query()).isEqualTo("timeoutMs=1500");
            }
        }
    }

    @Test
    void everyApproveResultSurvivesTheTrip() throws Exception {
        List<Governor.ApproveResult> results = List.of(
                new Governor.Applied(4, P),
                new Governor.NeedsRebuild(P, "Rebuild the app"),
                new Governor.Rejected("the address table has changed since this proposal"));
        for (Governor.ApproveResult result : results) {
            try (Stub s = serving(req -> GovernorHttp.approve(result))) {
                assertThat(new RemoteGovernor(s.url).approve(P.id(), T)).isEqualTo(result);
                assertThat(s.last.path()).isEqualTo("/governor/proposals/" + P.id() + "/approve");
            }
        }
    }

    @Test
    void looksUpAProposalOrFindsNone() throws Exception {
        try (Stub s = new Stub(req -> req.path().endsWith(P.id())
                ? new Stub.Reply(200, write(P)) : new Stub.Reply(404, write(GovernorHttp.notFound("x").body())))) {
            RemoteGovernor g = new RemoteGovernor(s.url);
            assertThat(g.proposal(P.id())).contains(P);
            assertThat(g.proposal("g9-other")).isEmpty();
        }
    }

    @Test
    void anUnreachableGovernorIsAResult() throws Exception {
        String url;
        try (Stub s = new Stub(req -> new Stub.Reply(200, "{}"))) {
            url = s.url;
        }
        assertThat(new RemoteGovernor(url).propose(T)).isInstanceOfSatisfying(Governor.Unavailable.class,
                u -> assertThat(u.reason()).startsWith("governor at " + url + ": unreachable"));
        assertThat(new RemoteGovernor(url).approve("g1-x", T)).isInstanceOf(Governor.Rejected.class);
    }

    @Test
    void theHttpSinkPostsTheTableToTheApp() throws Exception {
        try (Stub s = new Stub(req -> req.body().contains("bad")
                ? new Stub.Reply(400, "{\"error\":\"unknown binding type\",\"generation\":3}")
                : new Stub.Reply(200, "{\"generation\":4,\"routing\":\"...\"}"))) {
            HttpTableSink sink = new HttpTableSink(s.url);
            assertThat(sink.apply("capabilities: {}", "governor proposal g3-x")).isEqualTo(new TableSink.Accepted(4));
            assertThat(s.last.path()).isEqualTo("/admin/address-table");
            assertThat(s.last.source()).isEqualTo("governor proposal g3-x");
            assertThat(sink.apply("bad", "y")).isEqualTo(new TableSink.Refused("unknown binding type"));
        }
    }

    static String write(Object o) {
        try {
            return Json.MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

package dev.amorphic.inventory;

import java.util.List;

/** The app's evidence counters for one address table generation. */
public record EvidenceView(long generation, List<Served> served, List<Comparison> comparisons) {

    public record Served(String address, String binding, long total) {}

    public record Comparison(String served, String shadow, long total, long matches, long mismatches,
            long consecutiveMatches) {}

    public EvidenceView {
        served = List.copyOf(served);
        comparisons = List.copyOf(comparisons);
    }

    public long servedTotal() {
        return served.stream().mapToLong(Served::total).sum();
    }
}

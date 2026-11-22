package dev.amorphic.addressing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Chooses one candidate for a requested address (spec 01, section 7).
 *
 * <ul>
 *   <li>A candidate matches if it has the same kind and name, a version equal to the one asked for or inside
 *       the range asked for, and every qualifier asked for, matching exactly.</li>
 *   <li>A floating request (a range, or no version) skips withdrawn candidates. A pinned request still resolves
 *       to a withdrawn one, with a warning: it asked for exactly that version. Planned candidates are never
 *       resolved, because they aren't built.</li>
 *   <li>Of the matches, the caller's {@link Preference} chooses. Ties go to the first in canonical order.</li>
 *   <li>No match is a {@link Resolution.NoMatch} naming what was asked for and what is available. Nothing
 *       falls back.</li>
 * </ul>
 */
public final class Resolver {

    private Resolver() {}

    public static <C extends Candidate> Resolution<C> resolve(Address request, Collection<C> candidates,
            Preference preference) {
        List<C> same = candidates.stream()
                .filter(c -> c.address().kind().equals(request.kind()) && c.address().name().equals(request.name()))
                .toList();
        List<C> usable = new ArrayList<>();
        for (C c : same) {
            if (!request.matches(c.address()) || c.isPlanned() || (c.isWithdrawn() && !request.isPinned())) {
                continue;
            }
            usable.add(c);
        }
        Optional<C> chosen = switch (preference) {
            case HIGHEST_VERSION -> usable.stream().max(Comparator
                    .comparing((C c) -> c.address().version().orElseThrow())
                    .thenComparing(c -> c.address().toString(), Comparator.reverseOrder()));
        };
        if (chosen.isEmpty()) {
            return new Resolution.NoMatch<>(request, "nothing matches " + request + "; available: " + describe(same));
        }
        List<String> warnings = chosen.get().isWithdrawn()
                ? List.of(chosen.get().address() + " is withdrawn; resolved because it was asked for exactly")
                : List.of();
        return new Resolution.Resolved<>(chosen.get(), warnings);
    }

    private static String describe(List<? extends Candidate> available) {
        if (available.isEmpty()) {
            return "none";
        }
        return available.stream()
                .map(c -> c.address() + (c.isWithdrawn() ? " (withdrawn)" : c.isPlanned() ? " (planned)" : ""))
                .collect(Collectors.joining(", "));
    }
}

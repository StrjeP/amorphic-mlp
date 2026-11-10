package dev.amorphic.governor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** The governor's rule table (governor-rules.yaml). The first rule whose situation matches is used. */
public record RuleTable(List<Rule> rules) {

    static final Set<String> SITUATIONS = Set.of("shadow-withdrawn", "mismatch", "champion-alone-newer",
            "shadow-newer", "canary-below", "canary-at-least", "shadow-older", "champion-alone-remote");
    static final Set<String> PROPOSALS = Set.of("shadow-newest", "hold", "canary", "promote", "drop-shadow",
            "fold-in");

    /**
     * @param gate   minimum consecutive matches (with zero mismatches) before the rule may propose; null for none
     * @param weight canary weight the situation is measured against, and the weight a canary proposal uses
     */
    public record Rule(String id, String when, Integer gate, Integer weight, String propose, String why) {}

    public RuleTable {
        rules = List.copyOf(rules);
        for (Rule r : rules) {
            if (r.id() == null || !SITUATIONS.contains(r.when()) || !PROPOSALS.contains(r.propose())) {
                throw new IllegalArgumentException("invalid rule " + r.id() + ": when=" + r.when() + " propose="
                        + r.propose() + " (situations " + SITUATIONS + ", proposals " + PROPOSALS + ")");
            }
            boolean needsWeight = r.when().startsWith("canary-") || r.propose().equals("canary");
            if (needsWeight && (r.weight() == null || r.weight() < 0 || r.weight() > 100)) {
                throw new IllegalArgumentException("rule " + r.id() + " needs a weight between 0 and 100");
            }
        }
    }

    public static RuleTable load(Path path) {
        try {
            RuleTable t = new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                    .readValue(Files.readString(path), RuleTable.class);
            if (t == null || t.rules() == null || t.rules().isEmpty()) {
                throw new IllegalArgumentException("rule table " + path + " has no rules");
            }
            return t;
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read rule table " + path + ": " + e.getMessage(), e);
        }
    }
}

package dev.amorphic.governor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleTableTest {

    @Test
    void theRealRuleTableLoadsInOrder() {
        RuleTable t = RuleTable.load(Path.of("../../amorphic-demo-env/governor-rules.yaml"));
        assertThat(t.rules()).extracting(RuleTable.Rule::id).containsExactly("replace-withdrawn-shadow",
                "hold-on-mismatch", "start-shadow", "shadow-to-canary", "raise-canary", "promote", "deprecate",
                "fold-in");
    }

    @Test
    void rejectsUnknownSituationsAndProposals() {
        assertThatThrownBy(() -> new RuleTable(List.of(new RuleTable.Rule("x", "sunny", null, null, "hold", ""))))
                .hasMessageContaining("invalid rule x");
        assertThatThrownBy(() -> new RuleTable(List.of(new RuleTable.Rule("x", "mismatch", null, null, "panic", ""))))
                .hasMessageContaining("invalid rule x");
    }

    @Test
    void canaryRulesNeedAWeight() {
        assertThatThrownBy(() -> new RuleTable(List.of(new RuleTable.Rule("x", "shadow-newer", 1000, null, "canary", ""))))
                .hasMessageContaining("weight");
    }
}

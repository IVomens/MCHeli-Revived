package com.ivomens.mcheli.architecture;

import java.util.Map;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Fails a rule's test with every violation found. The rules are absolute: no rule has an allow-list or
 * any other way to accept a violation, so a violation is fixed in the code. See docs/ARCHITECTURE_RULES.md.
 */
final class RuleCheck {

    private RuleCheck() {
    }

    /**
     * Passes when nothing breaks the rule.
     *
     * @param rule  one sentence stating the rule, for the failure message
     * @param found every violation, mapped to a short piece of evidence
     */
    static void noViolations(String rule, SortedMap<String, String> found) {
        StringBuilder list = new StringBuilder();
        found.forEach((entry, evidence) -> list.append("  ").append(entry).append("\n      ").append(evidence)
                .append('\n'));
        failIfAny(rule, found, list);
    }

    /**
     * Passes when nothing is larger than the limit.
     *
     * @param tooLarge everything above the limit, mapped to its size
     */
    static void withinLimit(String rule, SortedMap<String, Integer> tooLarge, int limit, String unit) {
        StringBuilder list = new StringBuilder();
        tooLarge.forEach((entry, size) -> list.append("  ").append(entry).append(" has ").append(size).append(' ')
                .append(unit).append(", the limit is ").append(limit).append('\n'));
        failIfAny(rule, tooLarge, list);
    }

    private static void failIfAny(String rule, Map<String, ?> violations, StringBuilder list) {
        if (violations.isEmpty()) {
            return;
        }
        fail("Architecture rule broken: " + rule + "\n\nViolations (" + violations.size()
                + "). The rules have no exceptions; fix the code (see docs/ARCHITECTURE_RULES.md):\n" + list);
    }
}

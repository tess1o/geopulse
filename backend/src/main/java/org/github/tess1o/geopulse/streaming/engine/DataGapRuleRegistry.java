package org.github.tess1o.geopulse.streaming.engine;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ApplicationScoped
@Slf4j
public class DataGapRuleRegistry {
    private final Instance<DataGapRule> discoveredRules;
    private List<DataGapRule> orderedRules = List.of();

    @Inject
    public DataGapRuleRegistry(Instance<DataGapRule> discoveredRules) {
        this.discoveredRules = discoveredRules;
    }

    @PostConstruct
    void initialize() {
        List<DataGapRule> rules = StreamSupport.stream(discoveredRules.spliterator(), false).toList();
        orderedRules = sortAndValidate(rules);

        String orderedChain = orderedRules.stream()
                .map(rule -> rule.getClass().getSimpleName() + "(" + rule.order() + ")")
                .collect(Collectors.joining(" -> "));
        log.info("Initialized data gap rules chain: {}", orderedChain);
    }

    public List<DataGapRule> getOrderedRules() {
        return orderedRules;
    }

    static List<DataGapRule> sortAndValidate(List<DataGapRule> discoveredRules) {
        List<DataGapRule> sortedRules = discoveredRules.stream()
                .sorted(java.util.Comparator.comparingInt(DataGapRule::order))
                .toList();

        validateRules(sortedRules);
        return List.copyOf(sortedRules);
    }

    private static void validateRules(List<DataGapRule> rules) {
        if (rules.isEmpty()) {
            throw new IllegalStateException("No DataGapRule beans discovered");
        }

        Map<Integer, Long> orderCounts = rules.stream()
                .collect(Collectors.groupingBy(DataGapRule::order, Collectors.counting()));
        List<Integer> duplicateOrders = orderCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!duplicateOrders.isEmpty()) {
            throw new IllegalStateException("Duplicate DataGapRule order values detected: " + duplicateOrders);
        }

        boolean hasFallbackRule = rules.stream()
                .anyMatch(rule -> rule.order() == DataGapRule.ORDER_DEFAULT_GAP);
        if (!hasFallbackRule) {
            throw new IllegalStateException(
                    "No fallback DataGapRule found (expected order=" + DataGapRule.ORDER_DEFAULT_GAP + ")"
            );
        }
    }
}

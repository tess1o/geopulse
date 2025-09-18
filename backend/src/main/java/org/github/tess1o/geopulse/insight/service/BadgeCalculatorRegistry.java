package org.github.tess1o.geopulse.insight.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.insight.service.badge.BadgeCalculator;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Registry for badge calculators with cached lookup by badge ID.
 * Provides efficient access to calculators without iterating through all instances.
 */
@ApplicationScoped
@Slf4j
public class BadgeCalculatorRegistry {

    private final Instance<BadgeCalculator> badgeCalculators;
    private Map<String, BadgeCalculator> calculatorsByBadgeId;

    public BadgeCalculatorRegistry(Instance<BadgeCalculator> badgeCalculators) {
        this.badgeCalculators = badgeCalculators;
    }

    @PostConstruct
    void buildRegistry() {
        log.info("Building badge calculator registry");
        
        calculatorsByBadgeId = StreamSupport.stream(badgeCalculators.spliterator(), false)
                .collect(Collectors.toMap(
                    calculator -> {
                        try {
                            return calculator.getBadgeId();
                        } catch (Exception e) {
                            log.error("Failed to get badge ID from calculator {}: {}", 
                                    calculator.getClass().getSimpleName(), e.getMessage(), e);
                            return null;
                        }
                    },
                    Function.identity(),
                    (existing, replacement) -> {
                        log.warn("Duplicate badge ID found, keeping existing calculator: {}", 
                                existing.getClass().getSimpleName());
                        return existing;
                    }
                ))
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        log.info("Badge calculator registry built with {} calculators: {}", 
                calculatorsByBadgeId.size(), calculatorsByBadgeId.keySet());
    }

    /**
     * Get a badge calculator by badge ID
     */
    public BadgeCalculator getCalculator(String badgeId) {
        return calculatorsByBadgeId.get(badgeId);
    }

    /**
     * Get all available badge IDs
     */
    public Set<String> getAllBadgeIds() {
        return calculatorsByBadgeId.keySet();
    }
}
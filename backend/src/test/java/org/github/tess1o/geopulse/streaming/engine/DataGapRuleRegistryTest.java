package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.engine.datagap.model.DataGapContext;
import org.github.tess1o.geopulse.streaming.engine.datagap.rules.DataGapRule;
import org.github.tess1o.geopulse.streaming.engine.datagap.rules.DataGapRuleRegistry;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DataGapRuleRegistryTest {

    @Test
    void shouldSortRulesStrictlyByOrder() {
        List<DataGapRule> unsortedRules = List.of(
                new StubRule(DataGapRule.ORDER_TRIP_INFERENCE),
                new StubRule(DataGapRule.ORDER_DEFAULT_GAP),
                new StubRule(DataGapRule.ORDER_STAY_INFERENCE),
                new StubRule(DataGapRule.ORDER_SPARSE_STAY)
        );

        List<DataGapRule> orderedRules = DataGapRuleRegistry.sortAndValidate(unsortedRules);

        assertEquals(
                List.of(
                        DataGapRule.ORDER_STAY_INFERENCE,
                        DataGapRule.ORDER_SPARSE_STAY,
                        DataGapRule.ORDER_TRIP_INFERENCE,
                        DataGapRule.ORDER_DEFAULT_GAP
                ),
                orderedRules.stream().map(DataGapRule::order).toList()
        );
    }

    @Test
    void shouldFailWhenDuplicateOrderValuesExist() {
        List<DataGapRule> rulesWithDuplicateOrder = List.of(
                new StubRule(DataGapRule.ORDER_STAY_INFERENCE),
                new StubRule(DataGapRule.ORDER_STAY_INFERENCE),
                new StubRule(DataGapRule.ORDER_DEFAULT_GAP)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DataGapRuleRegistry.sortAndValidate(rulesWithDuplicateOrder)
        );

        assertTrue(exception.getMessage().contains("Duplicate DataGapRule order values detected"));
    }

    private static final class StubRule implements DataGapRule {
        private final int order;

        private StubRule(int order) {
            this.order = order;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public boolean apply(DataGapContext context, List<TimelineEvent> gapEvents) {
            return false;
        }
    }
}

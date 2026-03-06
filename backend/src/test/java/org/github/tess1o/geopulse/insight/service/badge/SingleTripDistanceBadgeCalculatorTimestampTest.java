package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SingleTripDistanceBadgeCalculatorTimestampTest {

    @Mock
    EntityManager entityManager;

    @Mock
    Query query;

    @InjectMocks
    SingleTripDistanceBadgeCalculator calculator;

    @Test
    void calculateSingleTripDistanceBadgeShouldHandleSqlTimestampFromNativeQuery() {
        UUID userId = UUID.randomUUID();
        Object[] row = new Object[]{
                12_000,
                Timestamp.from(Instant.parse("2026-03-06T17:05:34Z"))
        };

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("userId"), eq(userId))).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(row));

        Badge badge = calculator.calculateSingleTripDistanceBadge(
                userId,
                "distance_badge",
                "Distance Badge",
                "🏃",
                10_000,
                "Test");

        assertTrue(badge.isEarned());
        assertEquals("2026-03-06", badge.getEarnedDate());
    }
}

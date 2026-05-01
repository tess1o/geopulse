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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MovementTypeTripCountBadgeCalculatorTimestampTest {

    @Mock
    EntityManager entityManager;

    @Mock
    Query query;

    @InjectMocks
    MovementTypeTripCountBadgeCalculator calculator;

    @Test
    void calculateBadgeShouldHandleSqlTimestampFromNativeQuery() {
        UUID userId = UUID.randomUUID();
        Timestamp ts = Timestamp.from(Instant.parse("2026-03-06T17:05:34Z"));

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("userId"), eq(userId))).thenReturn(query);
        when(query.setParameter(eq("movementType"), eq("FLIGHT"))).thenReturn(query);
        when(query.setParameter(eq("threshold"), eq(5))).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{5L, ts});

        Badge badge = calculator.calculateMovementTypeTripCountBadge(
                userId,
                "flight_trips_5",
                "5 Flight Trips",
                "✈️",
                "FLIGHT",
                5,
                "Complete 5 flight trips"
        );

        assertTrue(badge.isEarned());
        assertEquals(100, badge.getProgress());
        assertEquals(5, badge.getCurrent());
        assertEquals(5, badge.getTarget());
        assertEquals("2026-03-06", badge.getEarnedDate());
    }

    @Test
    void calculateBadgeShouldReturnPartialProgressWhenThresholdNotReached() {
        UUID userId = UUID.randomUUID();

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("userId"), eq(userId))).thenReturn(query);
        when(query.setParameter(eq("movementType"), eq("TRAIN"))).thenReturn(query);
        when(query.setParameter(eq("threshold"), eq(10))).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{4L, null});

        Badge badge = calculator.calculateMovementTypeTripCountBadge(
                userId,
                "train_trips_10",
                "10 Train Trips",
                "🚆",
                "TRAIN",
                10,
                "Complete 10 train trips"
        );

        assertFalse(badge.isEarned());
        assertEquals(40, badge.getProgress());
        assertEquals(4, badge.getCurrent());
        assertEquals(10, badge.getTarget());
        assertNull(badge.getEarnedDate());
    }
}

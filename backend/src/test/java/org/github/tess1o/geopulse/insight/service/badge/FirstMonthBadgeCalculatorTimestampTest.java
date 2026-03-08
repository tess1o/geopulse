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
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FirstMonthBadgeCalculatorTimestampTest {

    @Mock
    EntityManager entityManager;

    @Mock
    Query firstDateQuery;

    @Mock
    Query trackedDaysQuery;

    @Mock
    Query startDateQuery;

    @InjectMocks
    FirstMonthBadgeCalculator calculator;

    @Test
    void calculateBadgeShouldHandleSqlTimestampFromNativeQuery() {
        UUID userId = UUID.randomUUID();

        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(firstDateQuery, trackedDaysQuery, startDateQuery);

        when(firstDateQuery.setParameter(eq("userId"), eq(userId))).thenReturn(firstDateQuery);
        when(firstDateQuery.getSingleResult()).thenReturn(LocalDate.parse("2026-01-01"));

        when(trackedDaysQuery.setParameter(eq("userId"), eq(userId))).thenReturn(trackedDaysQuery);
        when(trackedDaysQuery.getSingleResult()).thenReturn(31L);

        when(startDateQuery.setParameter(eq("userId"), eq(userId))).thenReturn(startDateQuery);
        when(startDateQuery.getSingleResult()).thenReturn(Timestamp.from(Instant.parse("2026-01-01T05:30:00Z")));

        Badge badge = calculator.calculateBadge(userId);

        assertTrue(badge.isEarned());
        assertEquals("January 1, 2026", badge.getEarnedDate());
    }
}

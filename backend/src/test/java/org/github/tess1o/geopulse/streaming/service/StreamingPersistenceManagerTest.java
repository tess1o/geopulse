package org.github.tess1o.geopulse.streaming.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.RawTimeline;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class StreamingPersistenceManagerTest {
    @Test
    void boundsDriverBatchesAndNeverManagesOrClearsInsertedEntities() throws Exception {
        var writer = new StreamingPersistenceManager();
        writer.entityManager = mock(EntityManager.class);
        writer.converter = new StreamingTimelineConverter(writer.entityManager);
        writer.objectMapper = new ObjectMapper();
        writer.memoryDiagnostics = mock(TimelineMemoryDiagnostics.class);
        UUID userId = UUID.randomUUID();
        var user = new UserEntity();
        user.setId(userId);
        when(writer.entityManager.getReference(UserEntity.class, userId)).thenReturn(user);
        Session session = mock(Session.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(writer.entityManager.unwrap(Session.class)).thenReturn(session);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        doAnswer(call -> { call.<Work>getArgument(0).execute(connection); return null; })
                .when(session).doWork(any());
        AtomicInteger pending = new AtomicInteger();
        List<Integer> sizes = new ArrayList<>();
        doAnswer(call -> { pending.incrementAndGet(); return null; }).when(statement).addBatch();
        when(statement.executeBatch()).thenAnswer(call -> {
            sizes.add(pending.get());
            return new int[pending.get()];
        });
        doAnswer(call -> { pending.set(0); return null; }).when(statement).clearBatch();
        var trip = Trip.builder().startTime(Instant.parse("2026-01-01T00:00:00Z"))
                .duration(Duration.ofMinutes(5)).tripType(TripType.CAR)
                .startPoint(new GPSPoint(50, 30, 0, 0)).endPoint(new GPSPoint(50.1, 30.1, 0, 0)).build();
        writer.persistRawTimeline(userId, RawTimeline.builder().trips(Collections.nCopies(1003, trip)).build(), null);
        assertThat(sizes).containsExactly(500, 500, 3);
        assertThat(pending.get()).isZero();
        verify(writer.entityManager).flush();
        verify(writer.entityManager, never()).clear();
        verify(session).doWork(any());
        verifyNoMoreInteractions(session);
        verify(statement).close();
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }
}

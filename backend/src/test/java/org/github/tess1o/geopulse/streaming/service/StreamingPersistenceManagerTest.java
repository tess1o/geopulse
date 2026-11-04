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
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class StreamingPersistenceManagerTest {
    @Test
    void boundsMultiRowStatementsAndNeverManagesOrClearsInsertedEntities() throws Exception {
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
        when(writer.entityManager.unwrap(Session.class)).thenReturn(session);
        doAnswer(call -> { call.<Work>getArgument(0).execute(connection); return null; })
                .when(session).doWork(any());
        List<Integer> sizes = new ArrayList<>();
        List<PreparedStatement> statements = new ArrayList<>();
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            int parameters = (int) sql.chars().filter(c -> c == '?').count();
            PreparedStatement statement = mock(PreparedStatement.class);
            statements.add(statement);
            Set<Integer> bound = new HashSet<>();
            doAnswer(binding -> { bound.add(binding.getArgument(0)); return null; })
                    .when(statement).setObject(anyInt(), any());
            doAnswer(binding -> { bound.add(binding.getArgument(0)); return null; })
                    .when(statement).setString(anyInt(), any());
            doAnswer(binding -> { bound.add(binding.getArgument(0)); return null; })
                    .when(statement).setLong(anyInt(), anyLong());
            when(statement.executeUpdate()).thenAnswer(execution -> {
                assertThat(bound).containsExactlyInAnyOrderElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, parameters).boxed().toList());
                sizes.add(parameters / 24);
                return parameters / 24;
            });
            return statement;
        });
        var trip = Trip.builder().startTime(Instant.parse("2026-01-01T00:00:00Z"))
                .duration(Duration.ofMinutes(5)).tripType(TripType.CAR)
                .startPoint(new GPSPoint(50, 30, 0, 0)).endPoint(new GPSPoint(50.1, 30.1, 0, 0)).build();
        List<Trip> trips = new ArrayList<>(Collections.nCopies(1003, trip));
        trips.add(0, null);
        trips.add(500, null);
        trips.add(null);
        writer.persistRawTimeline(userId, RawTimeline.builder().trips(trips).build(), null);
        assertThat(sizes).containsExactly(500, 500, 3);
        verify(writer.entityManager).flush();
        verify(writer.entityManager, never()).clear();
        verify(session).doWork(any());
        verifyNoMoreInteractions(session);
        for (PreparedStatement statement : statements) {
            verify(statement).close();
            verify(statement, never()).addBatch();
            verify(statement, never()).executeBatch();
        }
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }
}

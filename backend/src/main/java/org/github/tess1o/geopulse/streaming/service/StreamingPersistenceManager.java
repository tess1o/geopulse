package org.github.tess1o.geopulse.streaming.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.RawTimeline;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter;
import org.hibernate.Session;
import org.locationtech.jts.geom.Point;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded inserts on the generation transaction's connection, without retaining inserted entities. */
@ApplicationScoped
public class StreamingPersistenceManager {
    static final int JDBC_BATCH_SIZE = 500;

    private static final String INSERT_TRIP = """
            INSERT INTO timeline_trips (user_id,timestamp,trip_duration,start_point,end_point,distance_meters,
                movement_type,movement_type_source,avg_gps_speed,max_gps_speed,speed_variance,
                low_accuracy_points_count,water_distance_meters,water_distance_ratio,longest_water_segment_meters,
                water_sample_count,water_evidence_available,altitude_sustained_height_meters,
                altitude_vertical_speed_p90,altitude_sample_count,explanation,created_at,last_updated)
            VALUES {rows}
            """;
    private static final String TRIP_ROW = """
            (?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326),ST_SetSRID(ST_MakePoint(?,?),4326),
                ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,clock_timestamp(),clock_timestamp())
            """;
    private static final String INSERT_STAY = """
            INSERT INTO timeline_stays (user_id,timestamp,location,stay_duration,location_name,visit_name,
                favorite_id,geocoding_id,created_at,last_updated,location_source,explanation)
            SELECT v.user_id,v.timestamp,ST_SetSRID(ST_MakePoint(v.longitude,v.latitude),4326),
                v.duration,v.location_name,v.visit_name,f.id,g.id,clock_timestamp(),clock_timestamp(),
                CASE WHEN f.id IS NOT NULL THEN 'FAVORITE' WHEN g.id IS NOT NULL THEN 'GEOCODING'
                     ELSE 'HISTORICAL' END,v.explanation
            FROM (VALUES {rows}) v(user_id,timestamp,longitude,latitude,duration,location_name,visit_name,
                explanation,favorite_id,geocoding_id)
            LEFT JOIN favorite_locations f ON f.id=v.favorite_id
            LEFT JOIN reverse_geocoding_location g ON g.id=v.geocoding_id
            """;
    private static final String STAY_ROW = """
            (?::uuid,?::timestamptz,?::double precision,?::double precision,?::bigint,?::text,?::text,
                ?::jsonb,NULLIF(?::bigint,0),NULLIF(?::bigint,0))
            """;
    private static final String INSERT_GAP = """
            INSERT INTO timeline_data_gaps (user_id,start_time,end_time,duration_seconds,explanation,created_at)
            VALUES {rows}
            """;
    private static final String GAP_ROW = "(?,?,?,?,?::jsonb,clock_timestamp())";

    @Inject EntityManager entityManager;
    @Inject StreamingTimelineConverter converter;
    @Inject ObjectMapper objectMapper;
    @Inject TimelineMemoryDiagnostics memoryDiagnostics;

    public void persistRawTimeline(UUID userId, RawTimeline timeline, UUID jobId) {
        // Make pending ORM changes visible; never clear the caller's managed edits, notes or user lock state.
        entityManager.flush();
        var user = converter.createUserReference(userId);
        entityManager.unwrap(Session.class).doWork(connection -> {
            write(connection, INSERT_TRIP, TRIP_ROW, 23, timeline.getTrips(), (statement, offset, trip) ->
                    bindTrip(statement, offset, converter.convertStreamingTripToEntity(trip, user)), userId, jobId, "persist_trips");
            write(connection, INSERT_STAY, STAY_ROW, 10, timeline.getStays(), (statement, offset, stay) ->
                    bindStay(statement, offset, userId, stay), userId, jobId, "persist_stays");
            write(connection, INSERT_GAP, GAP_ROW, 5, timeline.getDataGaps(), (statement, offset, gap) ->
                    bindGap(statement, offset, userId, gap), userId, jobId, "persist_gaps");
        });
    }

    private <T> void write(Connection connection, String sql, String rowSql, int parameters, List<T> rows, Binder<T> binder,
                           UUID userId, UUID jobId, String stage) throws SQLException {
        if (rows.isEmpty()) return;
        List<T> batch = new ArrayList<>(JDBC_BATCH_SIZE);
        int count = 0;
        for (T row : rows) {
            if (row == null) continue;
            batch.add(row);
            if (batch.size() == JDBC_BATCH_SIZE) {
                insert(connection, sql, rowSql, parameters, batch, binder);
                count += batch.size();
                batch.clear();
                if (count % 2_000 == 0 && memoryDiagnostics.isEnabled()) {
                    memoryDiagnostics.checkpoint(userId, jobId, stage, Map.of(
                            "persistedRows", count, "totalRows", rows.size(), "jdbcBatchLimit", JDBC_BATCH_SIZE));
                }
            }
        }
        if (!batch.isEmpty()) {
            insert(connection, sql, rowSql, parameters, batch, binder);
            count += batch.size();
        }
        memoryDiagnostics.checkpoint(userId, jobId, stage + "_end", Map.of("persistedRows", count));
    }

    private <T> void insert(Connection connection, String sql, String rowSql, int parameters,
                            List<T> batch, Binder<T> binder) throws SQLException {
        String statementSql = sql.replace("{rows}", String.join(",", Collections.nCopies(batch.size(), rowSql)));
        try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
            for (int i = 0; i < batch.size(); i++) binder.bind(statement, i * parameters, batch.get(i));
            statement.executeUpdate();
        }
    }

    private void bindTrip(PreparedStatement s, int offset, TimelineTripEntity t) throws SQLException {
        s.setObject(offset + 1, t.getUser().getId());
        timestamp(s, offset + 2, t.getTimestamp());
        s.setLong(offset + 3, t.getTripDuration());
        point(s, offset + 4, t.getStartPoint());
        point(s, offset + 6, t.getEndPoint());
        s.setLong(offset + 8, t.getDistanceMeters());
        s.setString(offset + 9, t.getMovementType());
        s.setString(offset + 10, t.getMovementTypeSource().name());
        s.setObject(offset + 11, t.getAvgGpsSpeed());
        s.setObject(offset + 12, t.getMaxGpsSpeed());
        s.setObject(offset + 13, t.getSpeedVariance());
        s.setObject(offset + 14, t.getLowAccuracyPointsCount());
        s.setObject(offset + 15, t.getWaterDistanceMeters());
        s.setObject(offset + 16, t.getWaterDistanceRatio());
        s.setObject(offset + 17, t.getLongestWaterSegmentMeters());
        s.setObject(offset + 18, t.getWaterSampleCount());
        s.setObject(offset + 19, t.getWaterEvidenceAvailable());
        s.setObject(offset + 20, t.getAltitudeSustainedHeightMeters());
        s.setObject(offset + 21, t.getAltitudeVerticalSpeedP90());
        s.setObject(offset + 22, t.getAltitudeSampleCount());
        s.setString(offset + 23, json(t.getExplanation()));
    }

    private void bindStay(PreparedStatement s, int offset, UUID user, Stay stay) throws SQLException {
        s.setObject(offset + 1, user);
        timestamp(s, offset + 2, stay.getStartTime());
        point(s, offset + 3, GeoUtils.createPoint(stay.getLongitude(), stay.getLatitude()));
        s.setLong(offset + 5, stay.getDuration().toSeconds());
        s.setString(offset + 6, stay.getLocationName());
        s.setString(offset + 7, stay.getVisitName());
        s.setString(offset + 8, explanation(stay.getExplanation()));
        s.setObject(offset + 9, stay.getFavoriteId());
        s.setObject(offset + 10, stay.getGeocodingId());
    }

    private void bindGap(PreparedStatement s, int offset, UUID user, DataGap gap) throws SQLException {
        s.setObject(offset + 1, user);
        timestamp(s, offset + 2, gap.getStartTime());
        timestamp(s, offset + 3, gap.getEndTime());
        // Match the entity's @PrePersist epoch-second calculation, including fractional instants.
        s.setLong(offset + 4, gap.getEndTime().getEpochSecond() - gap.getStartTime().getEpochSecond());
        s.setString(offset + 5, explanation(gap.getExplanation()));
    }

    private String explanation(TimelineExplanation value) throws SQLException {
        return json(value == null || value.isEmpty() ? null : value.toMap());
    }

    private String json(Object value) throws SQLException {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new SQLException("Cannot serialize timeline explanation", e);
        }
    }

    private static void timestamp(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setObject(index, value == null ? null : value.atOffset(ZoneOffset.UTC));
    }

    private static void point(PreparedStatement statement, int index, Point value) throws SQLException {
        statement.setObject(index, value == null ? null : value.getX());
        statement.setObject(index + 1, value == null ? null : value.getY());
    }

    @FunctionalInterface
    private interface Binder<T> {
        void bind(PreparedStatement statement, int offset, T row) throws SQLException;
    }
}

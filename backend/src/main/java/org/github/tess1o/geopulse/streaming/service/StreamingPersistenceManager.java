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
            VALUES (?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326),ST_SetSRID(ST_MakePoint(?,?),4326),
                ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,clock_timestamp(),clock_timestamp())
            """;
    private static final String INSERT_STAY = """
            INSERT INTO timeline_stays (user_id,timestamp,location,stay_duration,location_name,visit_name,
                favorite_id,geocoding_id,created_at,last_updated,location_source,explanation)
            SELECT ?,?,ST_SetSRID(ST_MakePoint(?,?),4326),?,?,?,f.id,g.id,clock_timestamp(),clock_timestamp(),
                CASE WHEN f.id IS NOT NULL THEN 'FAVORITE' WHEN g.id IS NOT NULL THEN 'GEOCODING'
                     ELSE 'HISTORICAL' END,?::jsonb
            FROM (VALUES (NULLIF(?::bigint,0),NULLIF(?::bigint,0))) refs(favorite_id,geocoding_id)
            LEFT JOIN favorite_locations f ON f.id=refs.favorite_id
            LEFT JOIN reverse_geocoding_location g ON g.id=refs.geocoding_id
            """;
    private static final String INSERT_GAP = """
            INSERT INTO timeline_data_gaps (user_id,start_time,end_time,duration_seconds,explanation,created_at)
            VALUES (?,?,?,?,?::jsonb,clock_timestamp())
            """;

    @Inject EntityManager entityManager;
    @Inject StreamingTimelineConverter converter;
    @Inject ObjectMapper objectMapper;
    @Inject TimelineMemoryDiagnostics memoryDiagnostics;

    public void persistRawTimeline(UUID userId, RawTimeline timeline, UUID jobId) {
        // Make pending ORM changes visible; never clear the caller's managed edits, notes or user lock state.
        entityManager.flush();
        var user = converter.createUserReference(userId);
        entityManager.unwrap(Session.class).doWork(connection -> {
            write(connection, INSERT_TRIP, timeline.getTrips(), (statement, trip) ->
                    bindTrip(statement, converter.convertStreamingTripToEntity(trip, user)), userId, jobId, "persist_trips");
            write(connection, INSERT_STAY, timeline.getStays(), (statement, stay) ->
                    bindStay(statement, userId, stay), userId, jobId, "persist_stays");
            write(connection, INSERT_GAP, timeline.getDataGaps(), (statement, gap) ->
                    bindGap(statement, userId, gap), userId, jobId, "persist_gaps");
        });
    }

    private <T> void write(Connection connection, String sql, List<T> rows, Binder<T> binder,
                           UUID userId, UUID jobId, String stage) throws SQLException {
        if (rows.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int count = 0;
            for (T row : rows) {
                if (row == null) continue;
                binder.bind(statement, row);
                statement.addBatch();
                count++;
                if (count % JDBC_BATCH_SIZE == 0) {
                    statement.executeBatch();
                    statement.clearBatch();
                    if (count % 2_000 == 0 && memoryDiagnostics.isEnabled()) {
                        memoryDiagnostics.checkpoint(userId, jobId, stage, Map.of(
                                "persistedRows", count, "totalRows", rows.size(), "jdbcBatchLimit", JDBC_BATCH_SIZE));
                    }
                }
            }
            if (count % JDBC_BATCH_SIZE != 0) {
                statement.executeBatch();
                statement.clearBatch();
            }
            memoryDiagnostics.checkpoint(userId, jobId, stage + "_end", Map.of("persistedRows", count));
        }
    }

    private void bindTrip(PreparedStatement s, TimelineTripEntity t) throws SQLException {
        s.setObject(1, t.getUser().getId());
        timestamp(s, 2, t.getTimestamp());
        s.setLong(3, t.getTripDuration());
        point(s, 4, t.getStartPoint());
        point(s, 6, t.getEndPoint());
        s.setLong(8, t.getDistanceMeters());
        s.setString(9, t.getMovementType());
        s.setString(10, t.getMovementTypeSource().name());
        s.setObject(11, t.getAvgGpsSpeed());
        s.setObject(12, t.getMaxGpsSpeed());
        s.setObject(13, t.getSpeedVariance());
        s.setObject(14, t.getLowAccuracyPointsCount());
        s.setObject(15, t.getWaterDistanceMeters());
        s.setObject(16, t.getWaterDistanceRatio());
        s.setObject(17, t.getLongestWaterSegmentMeters());
        s.setObject(18, t.getWaterSampleCount());
        s.setObject(19, t.getWaterEvidenceAvailable());
        s.setObject(20, t.getAltitudeSustainedHeightMeters());
        s.setObject(21, t.getAltitudeVerticalSpeedP90());
        s.setObject(22, t.getAltitudeSampleCount());
        s.setString(23, json(t.getExplanation()));
    }

    private void bindStay(PreparedStatement s, UUID user, Stay stay) throws SQLException {
        s.setObject(1, user);
        timestamp(s, 2, stay.getStartTime());
        point(s, 3, GeoUtils.createPoint(stay.getLongitude(), stay.getLatitude()));
        s.setLong(5, stay.getDuration().toSeconds());
        s.setString(6, stay.getLocationName());
        s.setString(7, stay.getVisitName());
        s.setString(8, explanation(stay.getExplanation()));
        s.setObject(9, stay.getFavoriteId());
        s.setObject(10, stay.getGeocodingId());
    }

    private void bindGap(PreparedStatement s, UUID user, DataGap gap) throws SQLException {
        s.setObject(1, user);
        timestamp(s, 2, gap.getStartTime());
        timestamp(s, 3, gap.getEndTime());
        // Match the entity's @PrePersist epoch-second calculation, including fractional instants.
        s.setLong(4, gap.getEndTime().getEpochSecond() - gap.getStartTime().getEpochSecond());
        s.setString(5, explanation(gap.getExplanation()));
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
        void bind(PreparedStatement statement, T row) throws SQLException;
    }
}

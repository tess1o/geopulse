package org.github.tess1o.geopulse.streaming.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Timeline responses without entity graphs or dirty-checking snapshots. */
@ApplicationScoped
public class TimelineReadRepository {
    @Inject EntityManager entityManager;

    public List<TimelineStayLocationDTO> findStays(UUID userId, Instant start, Instant end) {
        return findStays(userId, start, end, Integer.MAX_VALUE);
    }

    public List<TimelineStayLocationDTO> findStays(UUID userId, Instant start, Instant end, int limit) {
        return entityManager.createQuery("""
                select s.id as id, s.timestamp as timestamp, st_x(s.location) as longitude,
                    st_y(s.location) as latitude, s.stayDuration as duration, s.locationName as locationName,
                    s.visitName as visitName, f.id as favoriteId, g.id as geocodingId,
                    case when f.id is not null then f.city else g.city end as city,
                    case when f.id is not null then f.country else g.country end as country,
                    s.explanation as explanation
                from TimelineStayEntity s left join s.favoriteLocation f left join s.geocodingLocation g
                where s.user.id = :userId and ((s.timestamp >= :start and s.timestamp <= :end)
                    or (s.timestamp < :start and timestampadd(second, s.stayDuration, s.timestamp) > :start))
                order by s.timestamp
                """, Tuple.class)
                .setParameter("userId", userId).setParameter("start", start).setParameter("end", end)
                .setMaxResults(limit)
                .getResultList().stream().map(row -> TimelineStayLocationDTO.builder()
                        .id(row.get("id", Long.class)).timestamp(row.get("timestamp", Instant.class))
                        .longitude(row.get("longitude", Double.class)).latitude(row.get("latitude", Double.class))
                        .stayDuration(row.get("duration", Long.class))
                        .locationName(row.get("locationName") == null ? "Unknown location" : row.get("locationName", String.class))
                        .visitName(row.get("visitName", String.class))
                        .favoriteId(row.get("favoriteId", Long.class)).geocodingId(row.get("geocodingId", Long.class))
                        .city(row.get("city", String.class)).country(row.get("country", String.class))
                        .explanation(explanation(row)).build()).toList();
    }

    public List<TimelineTripDTO> findTrips(UUID userId, Instant start, Instant end) {
        return findTrips(userId, start, end, Integer.MAX_VALUE);
    }

    public List<TimelineTripDTO> findTrips(UUID userId, Instant start, Instant end, int limit) {
        return entityManager.createQuery("""
                select t.id as id, t.timestamp as timestamp, st_x(t.startPoint) as longitude,
                    st_y(t.startPoint) as latitude, st_x(t.endPoint) as endLongitude,
                    st_y(t.endPoint) as endLatitude, t.tripDuration as duration,
                    t.distanceMeters as distance, t.movementType as movementType,
                    t.movementTypeSource as movementTypeSource, t.manualRoute as manualRoute, t.explanation as explanation
                from TimelineTripEntity t
                where t.user.id = :userId and ((t.timestamp >= :start and t.timestamp <= :end)
                    or (t.timestamp < :start and timestampadd(second, t.tripDuration, t.timestamp) > :start))
                order by t.timestamp
                """, Tuple.class)
                .setParameter("userId", userId).setParameter("start", start).setParameter("end", end)
                .setMaxResults(limit)
                .getResultList().stream().map(row -> TimelineTripDTO.builder()
                        .id(row.get("id", Long.class)).timestamp(row.get("timestamp", Instant.class))
                        .longitude(row.get("longitude", Double.class)).latitude(row.get("latitude", Double.class))
                        .endLongitude(row.get("endLongitude", Double.class)).endLatitude(row.get("endLatitude", Double.class))
                        .tripDuration(row.get("duration", Long.class)).distanceMeters(row.get("distance", Long.class))
                        .movementType(row.get("movementType", String.class))
                        .movementTypeSource(row.get("movementTypeSource") == null ? MovementTypeSource.AUTO.name()
                                : row.get("movementTypeSource", MovementTypeSource.class).name())
                        .manualRoute((java.util.List<org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto>) row.get("manualRoute"))
                        .explanation(explanation(row)).build()).toList();
    }

    public List<TimelineDataGapDTO> findGaps(UUID userId, Instant start, Instant end, Instant latestGpsTimestamp) {
        return findGaps(userId, start, end, latestGpsTimestamp, Integer.MAX_VALUE);
    }

    public List<TimelineDataGapDTO> findGaps(UUID userId, Instant start, Instant end, Instant latestGpsTimestamp, int limit) {
        return entityManager.createQuery("""
                select g.id as id, g.startTime as startTime, g.endTime as endTime, g.explanation as explanation
                from TimelineDataGapEntity g where g.user.id = :userId and
                    ((g.startTime >= :start and g.startTime <= :end)
                    or (g.endTime >= :start and g.endTime <= :end)
                    or (g.startTime <= :start and g.endTime >= :end)
                    or (g.startTime < :start and g.endTime > :start))
                order by g.startTime
                """, Tuple.class)
                .setParameter("userId", userId).setParameter("start", start).setParameter("end", end)
                .setMaxResults(limit)
                .getResultList().stream().map(row -> {
                    Instant gapStart = row.get("startTime", Instant.class);
                    var dto = new TimelineDataGapDTO(row.get("id", Long.class), gapStart,
                            row.get("endTime", Instant.class), latestGpsTimestamp != null && latestGpsTimestamp.equals(gapStart));
                    dto.setExplanation(explanation(row));
                    return dto;
                }).toList();
    }

    @SuppressWarnings("unchecked")
    private static TimelineExplanationDTO explanation(Tuple row) {
        return TimelineExplanationDTO.from((Map<String, Object>) row.get("explanation"));
    }
}

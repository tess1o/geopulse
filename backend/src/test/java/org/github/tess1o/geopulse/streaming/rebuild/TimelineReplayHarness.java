package org.github.tess1o.geopulse.streaming.rebuild;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.export.dto.TimelineDataDto;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.importdata.model.DebugImportRequest;
import org.github.tess1o.geopulse.importdata.model.DebugTimelineComparison;
import org.github.tess1o.geopulse.importdata.service.DebugTimelineComparisonService;
import org.github.tess1o.geopulse.importdata.service.DebugImportService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.jobs.RealTimeTimelineJob;
import org.github.tess1o.geopulse.streaming.model.TimelineRebuildRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineRebuildRequestRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.RealtimeTimelineProcessor;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays recorded GPS data through the live ingest path the way a phone delivers it, runs the real-time timeline
 * pass in between, and captures the resulting timeline so it can be compared with a clean full rebuild.
 */
@ApplicationScoped
public class TimelineReplayHarness {

    /** A real day recorded with OwnTracks (GitHub issue #627): office stay, drive home, data gap, home stay. */
    public static final String ISSUE_627_EXPORT = "/timeline-fixtures/issue-627-debug-export.zip";

    @Inject
    DebugImportService debugImportService;

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    GpsPointService gpsPointService;

    @Inject
    RealTimeTimelineJob realTimeTimelineJob;

    @Inject
    @Identifier("timeline-processing")
    ExecutorService timelineExecutor;

    @Inject
    TimelineRebuildRequestRepository rebuildRequestRepository;

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    DebugTimelineComparisonService comparisonService;

    /** A new user with the export's timeline settings and favorites, but none of its GPS points. */
    public UUID createUserWithSettingsOf(String export, String prefix) {
        return createUserWithSettingsOf(readResource(export), prefix);
    }

    /** A new user with the export's timeline settings, favorites, geocoding and overrides, but none of its points. */
    public UUID createUserWithSettingsOf(byte[] export, String prefix) {
        UUID userId = testUserFactory.createUser(prefix).userId();
        try {
            debugImportService.importDebugData(userId, withoutGpsPoints(export),
                    new DebugImportRequest(true, true));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return userId;
    }

    /** A new user with everything in the export: points, settings, favorites, geocoding and overrides. */
    public UUID importExport(byte[] export, String prefix) {
        UUID userId = testUserFactory.createUser(prefix).userId();
        try {
            debugImportService.importDebugData(userId, export, new DebugImportRequest(true, true));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return userId;
    }

    /** A new user with default timeline settings. */
    public UUID createUser(String prefix) {
        return testUserFactory.createUser(prefix).userId();
    }

    /** The end of the ongoing data gap, which only the gap maintenance moves. */
    public Instant ongoingGapEnd(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() -> (Instant) entityManager.createQuery(
                        "select max(g.endTime) from TimelineDataGapEntity g where g.user.id = :userId")
                .setParameter("userId", userId)
                .getSingleResult());
    }

    /** Ids of the user's GPS points from {@code from} (inclusive) to {@code to} (exclusive). */
    public List<Long> pointIds(UUID userId, Instant from, Instant to) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select p.id from GpsPointEntity p where p.user.id = :userId "
                                + "and p.timestamp >= :from and p.timestamp < :to", Long.class)
                .setParameter("userId", userId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList());
    }

    public String lastRebuildError(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() -> (String) entityManager.createNativeQuery(
                        "SELECT last_error FROM timeline_rebuild_requests WHERE user_id = ?1")
                .setParameter(1, userId)
                .getSingleResult());
    }

    /** Pretends the last failure happened {@code ago}, so the retry delay has passed. */
    public void backdateLastFailure(UUID userId, java.time.Duration ago) {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                        "UPDATE timeline_rebuild_requests SET last_failed_at = ?2 WHERE user_id = ?1")
                .setParameter(1, userId)
                .setParameter(2, Instant.now().minus(ago))
                .executeUpdate());
    }

    /** A new user with default timeline settings and a favorite area around each place. */
    public UUID createUserWithFavoriteAreas(String prefix, List<SyntheticDays.Place> places) {
        UUID userId = testUserFactory.createUser(prefix).userId();
        try {
            List<Map<String, Object>> areas = new ArrayList<>();
            for (SyntheticDays.Place place : places) {
                double dLat = 90 / 111_320.0;
                double dLon = 90 / (111_320.0 * Math.cos(Math.toRadians(place.lat())));
                areas.add(Map.of("name", place.name(), "type", "AREA",
                        "northEastLat", place.lat() + dLat, "northEastLon", place.lon() + dLon,
                        "southWestLat", place.lat() - dLat, "southWestLon", place.lon() - dLon));
            }
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put("metadata.json", objectMapper.writeValueAsBytes(Map.of(
                    "gps_point_count", 0, "favorite_locations_count", 0, "favorite_areas_count", areas.size())));
            entries.put("gps_data.json", "[]".getBytes(StandardCharsets.UTF_8));
            entries.put("favorite_locations.json", "[]".getBytes(StandardCharsets.UTF_8));
            entries.put("favorite_areas.json", objectMapper.writeValueAsBytes(areas));
            debugImportService.importDebugData(userId, zip(entries), new DebugImportRequest(true, false));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return userId;
    }

    /** The export's GPS messages, oldest first. */
    public List<OwnTracksLocationMessage> messages(String export) {
        return messages(readResource(export));
    }

    /** The export's GPS messages, oldest first. */
    public List<OwnTracksLocationMessage> messages(byte[] export) {
        try {
            JsonNode gpsData = objectMapper.readTree(readZipEntry(export, "gps_data.json"));
            List<OwnTracksLocationMessage> messages = new ArrayList<>();
            for (JsonNode node : gpsData) {
                messages.add(objectMapper.treeToValue(node, OwnTracksLocationMessage.class));
            }
            messages.sort(Comparator.comparing(OwnTracksLocationMessage::getTst));
            return messages;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Groups messages into the batches a phone uploading every {@code minutes} minutes would send. */
    public static List<List<OwnTracksLocationMessage>> uploadBatches(List<OwnTracksLocationMessage> messages,
                                                                     int minutes) {
        Map<Long, List<OwnTracksLocationMessage>> batches = new LinkedHashMap<>();
        for (OwnTracksLocationMessage message : messages) {
            batches.computeIfAbsent(message.getTst() / (minutes * 60L), ignored -> new ArrayList<>()).add(message);
        }
        return new ArrayList<>(batches.values());
    }

    /**
     * Groups messages into the requests the exporting server actually received them in: in order of their arrival
     * time ({@code created_at}), starting a new request whenever arrivals are more than a second apart. Messages
     * without an arrival time are treated as arriving when they were recorded.
     */
    public static List<List<OwnTracksLocationMessage>> arrivalBatches(List<OwnTracksLocationMessage> messages) {
        List<OwnTracksLocationMessage> byArrival = new ArrayList<>(messages);
        byArrival.sort(Comparator.comparing(TimelineReplayHarness::arrival)
                .thenComparing(OwnTracksLocationMessage::getTst));
        List<List<OwnTracksLocationMessage>> batches = new ArrayList<>();
        Long previousArrival = null;
        for (OwnTracksLocationMessage message : byArrival) {
            long arrival = arrival(message);
            if (previousArrival == null || arrival - previousArrival > 1) {
                batches.add(new ArrayList<>());
            }
            batches.getLast().add(message);
            previousArrival = arrival;
        }
        return batches;
    }

    private static long arrival(OwnTracksLocationMessage message) {
        return message.getCreatedAt() != null ? message.getCreatedAt() : message.getTst();
    }

    /**
     * Replays the export's points the way the exporting server received them, running a real-time pass after each
     * request: the user's timeline may have been built this way rather than by a full rebuild.
     */
    public void replayByArrival(UUID userId, byte[] export) {
        replayLive(userId, arrivalBatches(messages(export)));
    }

    /** Sends messages through the OwnTracks ingest path in one request, as the live endpoint does. */
    public void ingest(UUID userId, List<OwnTracksLocationMessage> messages) {
        GpsSourceConfigEntity source = GpsSourceConfigEntity.builder()
                .sourceType(GpsSourceType.OWNTRACKS)
                .active(true)
                .build();
        QuarkusTransaction.requiringNew().run(() -> {
            for (OwnTracksLocationMessage message : messages) {
                gpsPointService.saveOwnTracksGpsPoint(message, userId, message.getTid(), GpsSourceType.OWNTRACKS,
                        source);
            }
        });
    }

    /**
     * One pass of the real-time job for this user, exactly as the scheduler runs it, returning what it did. It runs on
     * the timeline executor, where neither a transaction nor a request context is active: the test thread has a
     * request context, which would hide database access outside a transaction.
     */
    public RealtimeTimelineProcessor.Outcome process(UUID userId) {
        UserEntity user = QuarkusTransaction.requiringNew()
                .call(() -> entityManager.find(UserEntity.class, userId));
        return CompletableFuture.supplyAsync(() -> realTimeTimelineJob.processUser(user), timelineExecutor).join();
    }

    /** Delivers each batch, running a real-time pass after it, as a phone uploading through the day would. */
    public void replayLive(UUID userId, List<List<OwnTracksLocationMessage>> batches) {
        for (List<OwnTracksLocationMessage> batch : batches) {
            ingest(userId, batch);
            process(userId);
        }
    }

    public Optional<TimelineRebuildRequest> pendingRebuild(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() -> rebuildRequestRepository.find(userId));
    }

    /**
     * The real-time result must be exactly what a clean rebuild of all stored points produces. Leaves the user with
     * the clean rebuild.
     */
    public void assertMatchesFullRebuild(UUID userId, String scenario) {
        assertThat(pendingRebuild(userId)).as("%s: no rebuild left pending", scenario).isEmpty();
        TimelineSnapshot realtime = snapshot(userId);
        fullRebuild(userId);
        TimelineSnapshot clean = snapshot(userId);
        assertThat(realtime.content())
                .as("%s: real-time timeline differs from a full rebuild.%nreal-time:%n  %s%nfull rebuild:%n  %s",
                        scenario, String.join("\n  ", realtime.content()), String.join("\n  ", clean.content()))
                .containsExactlyElementsOf(clean.content());
    }

    /** A clean rebuild of the whole timeline from the stored points: the reference result. */
    public void fullRebuild(UUID userId) {
        timelineGenerationService.regenerateFullTimeline(userId);
    }

    public TimelineSnapshot snapshot(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Instant latestPoint = (Instant) entityManager.createQuery(
                            "select max(p.timestamp) from GpsPointEntity p where p.user.id = :userId")
                    .setParameter("userId", userId)
                    .getSingleResult();

            List<String> stays = stayRepository.find("user.id = ?1 order by timestamp", userId).stream()
                    .map(TimelineReplayHarness::describe)
                    .toList();
            List<String> trips = tripRepository.find("user.id = ?1 order by timestamp", userId).stream()
                    .map(TimelineReplayHarness::describe)
                    .toList();
            List<String> gaps = dataGapRepository.find("user.id = ?1 order by startTime", userId).stream()
                    .map(gap -> describe(gap, latestPoint))
                    .toList();
            List<Long> stayIds = stayRepository.find("user.id = ?1 order by timestamp", userId).stream()
                    .map(TimelineStayEntity::getId)
                    .toList();
            return new TimelineSnapshot(stays, trips, gaps, stayIds);
        });
    }

    /**
     * The timeline overlapping the export's requested range, as times, durations and movement types only: names and
     * ids differ between the exporting server and this one.
     */
    public List<String> structure(UUID userId, byte[] export) {
        ExportRanges ranges = exportRanges(export);
        return QuarkusTransaction.requiringNew().call(() -> comparisonService
                .currentEvents(userId, ranges.requestedStart(), ranges.requestedEnd(), ranges.latestPoint())
                .stream()
                .map(DebugTimelineComparison.Event::describe)
                .toList());
    }

    /** The exporting server's timeline ({@code timeline.json}) in the form of {@link #structure(UUID, byte[])}. */
    public List<String> exportedStructure(byte[] export) {
        ExportRanges ranges = exportRanges(export);
        return comparisonService
                .exportedEvents(exportedTimeline(export), ranges.requestedStart(), ranges.requestedEnd(),
                        ranges.latestPoint())
                .stream()
                .map(DebugTimelineComparison.Event::describe)
                .toList();
    }

    /** The user's timeline must be the one the export says the exporting server showed, within the requested range. */
    public void assertMatchesExportedTimeline(UUID userId, byte[] export) {
        assertThat(structure(userId, export))
                .as("timeline differs from the exported one")
                .containsExactlyElementsOf(exportedStructure(export));
    }

    private TimelineDataDto exportedTimeline(byte[] export) {
        try {
            return objectMapper.readValue(readZipEntry(export, "timeline.json"), TimelineDataDto.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ExportRanges exportRanges(byte[] export) {
        try {
            JsonNode metadata = objectMapper.readTree(readZipEntry(export, "metadata.json"));
            JsonNode requested = metadata.path("requested_range");
            Instant latestPoint = null;
            for (JsonNode point : objectMapper.readTree(readZipEntry(export, "gps_data.json"))) {
                Instant timestamp = point.hasNonNull("tst_ms")
                        ? Instant.ofEpochMilli(point.get("tst_ms").asLong())
                        : Instant.ofEpochSecond(point.get("tst").asLong());
                if (latestPoint == null || timestamp.isAfter(latestPoint)) {
                    latestPoint = timestamp;
                }
            }
            return new ExportRanges(Instant.parse(requested.get("start").asText()),
                    Instant.parse(requested.get("end").asText()), latestPoint);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record ExportRanges(Instant requestedStart, Instant requestedEnd, Instant latestPoint) {
    }

    private static String describe(TimelineStayEntity stay) {
        return "STAY " + stay.getTimestamp() + " " + stay.getStayDuration() + "s "
                + String.format(Locale.ROOT, "(%.6f,%.6f)", stay.getLocation().getY(), stay.getLocation().getX())
                + " '" + stay.getLocationName()
                + "' favorite=" + (stay.getFavoriteLocation() == null ? null : stay.getFavoriteLocation().getId())
                + " geocoding=" + (stay.getGeocodingLocation() == null ? null : stay.getGeocodingLocation().getId())
                + " why=" + explanation(stay.getExplanation());
    }

    private static String describe(TimelineTripEntity trip) {
        return "TRIP " + trip.getTimestamp() + " " + trip.getTripDuration() + "s " + trip.getDistanceMeters() + "m "
                + trip.getMovementType() + " why=" + explanation(trip.getExplanation());
    }

    /** The whole explanation: start, notes and end, each with the values it was based on. */
    private static String explanation(Map<String, Object> stored) {
        TimelineExplanation explanation = TimelineExplanation.fromMap(stored);
        if (explanation == null) {
            return "none";
        }
        return reason(explanation.getStart()) + " " + explanation.getNotes().stream()
                .map(TimelineReplayHarness::reason).toList() + " " + reason(explanation.getEnd());
    }

    private static String reason(TimelineReason reason) {
        return reason == null ? "-" : reason.params().isEmpty() ? reason.code() : reason.code() + reason.params();
    }

    /** The ongoing gap ends at whatever "now" was when it was last extended, so only its start is compared. */
    private static String describe(TimelineDataGapEntity gap, Instant latestPoint) {
        boolean ongoing = Objects.equals(gap.getStartTime(), latestPoint);
        return "GAP " + gap.getStartTime() + " why=" + explanation(gap.getExplanation()) + " .. "
                + (ongoing ? "ongoing" : gap.getEndTime());
    }

    public static byte[] readResource(String path) {
        try (InputStream input = TimelineReplayHarness.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing test fixture " + path);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] readZipEntry(byte[] zip, String name) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return input.readAllBytes();
                }
            }
        }
        throw new IllegalArgumentException("Missing " + name + " in export");
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] withoutGpsPoints(byte[] zip) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip));
             ZipOutputStream copy = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] content = input.readAllBytes();
                if (entry.getName().equals("gps_data.json")) {
                    content = "[]".getBytes(StandardCharsets.UTF_8);
                }
                copy.putNextEntry(new ZipEntry(entry.getName()));
                copy.write(content);
                copy.closeEntry();
            }
        }
        return output.toByteArray();
    }

    /** The timeline in a comparable form, plus stay ids to tell whether a pass rebuilt anything. */
    public record TimelineSnapshot(List<String> stays, List<String> trips, List<String> gaps, List<Long> stayIds) {

        /** The timeline content without ids. */
        public List<String> content() {
            List<String> all = new ArrayList<>();
            all.addAll(stays);
            all.addAll(trips);
            all.addAll(gaps);
            return all;
        }

        /** The content without favorite and geocoding ids, to compare two users with the same places. */
        public List<String> contentAcrossUsers() {
            return content().stream()
                    .map(item -> item.replaceAll(" favorite=\\S+ geocoding=\\S+", ""))
                    .toList();
        }
    }
}

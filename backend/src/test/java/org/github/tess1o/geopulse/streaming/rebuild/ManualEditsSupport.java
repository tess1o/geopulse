package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportFormat;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.service.ExportDataGenerator;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.model.dto.TripStaySplitRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.github.tess1o.geopulse.streaming.service.TripStaySplitOverrideService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Manual timeline corrections the way the timeline page makes them, for scenario tests. */
@ApplicationScoped
public class ManualEditsSupport {

    @Inject
    TripStaySplitOverrideService splitService;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineJobProgressService jobProgressService;

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    AsyncTimelineGenerationService asyncTimelineGenerationService;

    @Inject
    ExportDataGenerator exportDataGenerator;

    @Inject
    ImportDataService importDataService;

    /** The data types a user moving to another server takes along for the timeline: points, places, corrections. */
    private static final List<String> TIMELINE_DATA_TYPES = List.of(
            ExportImportConstants.DataTypes.RAW_GPS,
            ExportImportConstants.DataTypes.FAVORITES,
            ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION,
            ExportImportConstants.DataTypes.TIMELINE_OVERRIDES);

    /** The stored trip that runs through the whole window. */
    public TimelineTripEntity tripCovering(UUID userId, Instant from, Instant to) {
        return QuarkusTransaction.requiringNew().call(() -> tripRepository.find("user.id = ?1 order by timestamp", userId)
                .stream()
                .filter(trip -> trip.getTimestamp().isBefore(from)
                        && trip.getTimestamp().plusSeconds(trip.getTripDuration()).isAfter(to))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No trip runs from before " + from + " to after " + to)));
    }

    /**
     * Turns the part of the trip from {@code from} to {@code to} into a stay at {@code place}, anchored at the middle
     * of the window, as the split dialog does. Returns the override id.
     */
    public Long split(UUID userId, Instant from, Instant to, double[] place, String name) {
        TimelineTripEntity trip = tripCovering(userId, from, to);
        TripStaySplitRequest request = new TripStaySplitRequest();
        request.setStayStartTime(from);
        request.setStayEndTime(to);
        request.setAnchorTimestamp(from.plus(Duration.between(from, to).dividedBy(2)));
        request.setLatitude(place[0]);
        request.setLongitude(place[1]);
        request.setLocationName(name);
        return splitService.splitTrip(userId, trip.getId(), request).orElseThrow().overrideId();
    }

    /** Undoes a split as the undo endpoint does, and waits for the regeneration it starts. */
    public void undoSplit(UUID userId, Long overrideId) {
        Instant from = timelineGenerationService.resetTripStaySplitOverride(userId, overrideId).orElseThrow()
                .regenerationStartTime();
        UUID jobId = asyncTimelineGenerationService.scheduleTimelineRegenerationFromTimestamp(userId, from).jobId();
        awaitJob(jobId);
    }

    /** A GeoPulse export of the user's timeline data from {@code from} to {@code to}. */
    public byte[] exportOf(UUID userId, Instant from, Instant to) {
        ExportDateRange range = new ExportDateRange();
        range.setStartDate(from);
        range.setEndDate(to);
        ExportJob job = new ExportJob(userId, TIMELINE_DATA_TYPES, range, ExportFormat.GEOPULSE);
        return QuarkusTransaction.requiringNew().call(() -> {
            exportDataGenerator.generateGeoPulseNativeExport(job);
            return Files.readAllBytes(Path.of(job.getTempFilePath()));
        });
    }

    /** Imports a GeoPulse export for the user, as the import page does; the timeline is regenerated from it. */
    public void importInto(UUID userId, byte[] export) {
        ImportOptions options = new ImportOptions();
        options.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        options.setDataTypes(TIMELINE_DATA_TYPES);
        try {
            importDataService.processImportData(new ImportJob(userId, options, "timeline-export.zip", export));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Waits until no timeline job of the user is running. */
    public void awaitTimelineJobs(UUID userId) {
        Instant deadline = Instant.now().plusSeconds(60);
        while (jobProgressService.getUserActiveJob(userId).filter(job -> !job.isTerminal()).isPresent()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timeline job of user " + userId + " did not finish in time");
            }
            sleep();
        }
    }

    /** Waits for one timeline job to finish and returns how it ended. */
    public TimelineJobProgress awaitJob(UUID jobId) {
        Instant deadline = Instant.now().plusSeconds(60);
        while (true) {
            TimelineJobProgress job = jobProgressService.getJobProgress(jobId)
                    .orElseThrow(() -> new AssertionError("Unknown timeline job " + jobId));
            if (job.isTerminal()) {
                return job;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timeline job " + jobId + " did not finish in time");
            }
            sleep();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}

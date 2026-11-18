package org.github.tess1o.geopulse.streaming.rebuild;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionOp;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An export made before the corrections model carries its corrections only in {@code timeline-overrides.json}. Old
 * exports list split overrides by stay start time, so a split made on a leg of an already split trip comes before its
 * parent. Importing such an archive converts every override into a correction: both nested stays, the split's leg
 * movement types and a stay name all apply, the corrections undo independently, and importing the archive a second
 * time is refused instead of duplicating them.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ImportedLegacyNestedSplitIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    TimelineEditService timelineEdits;

    @Inject
    TimelineStayRepository stays;

    @Inject
    ObjectMapper json;

    private final CommuteWithStops day = new CommuteWithStops();
    private final double[] onTheWay = day.track.place(49.8392, 24.0483);

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void legacyOverridesImportAsIndependentCorrectionsAndCannotBeImportedTwice() throws Exception {
        UUID original = day.ingest(harness, "legacy-overrides-export");
        var trip = edits.tripCovering(original, at("08:12"), at("08:20"));
        Instant tripStart = trip.getTimestamp();
        long tripSeconds = trip.getTripDuration();
        org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity office = QuarkusTransaction.requiringNew().call(() -> stays.find("user.id = ?1 and timestamp >= ?2 order by timestamp",
                original, at("08:40")).firstResult());
        Instant officeStart = office.getTimestamp();
        Instant officeEnd = officeStart.plusSeconds(office.getStayDuration());

        // Old exports ordered splits by stay start: the child on the first leg precedes its parent.
        var child = split(at("08:12"), at("08:14"), onTheWay, "Coffee", tripStart,
                at("08:16").getEpochSecond() - tripStart.getEpochSecond(), null);
        var parent = split(at("08:16"), at("08:20"), day.bakery, "Bakery", tripStart, tripSeconds, "CAR");
        var overrides = new LinkedHashMap<String, Object>();
        overrides.put("dataType", "timelineoverrides");
        overrides.put("tripMovementOverrides", List.of());
        overrides.put("dataGapStayOverrides", List.of());
        overrides.put("tripStaySplitOverrides", List.of(child, parent));
        overrides.put("stayNameOverrides", List.of(Map.of(
                "stayStartTime", officeStart.toString(), "stayEndTime", officeEnd.toString(), "locationName", "Desk")));
        byte[] legacyExport = asLegacyExport(edits.exportOf(original, at("00:00"), at("23:59")), overrides);

        UUID imported = harness.createUser("legacy-overrides-import");
        edits.importInto(imported, legacyExport);
        edits.awaitTimelineJobs(imported);
        harness.fullRebuild(imported);

        assertThat(harness.snapshot(imported).stays())
                .as("both nested split stays after importing an old-ordered legacy export")
                .anySatisfy(stay -> assertThat(stay).contains("'Bakery'"))
                .anySatisfy(stay -> assertThat(stay).contains("'Coffee'"));
        org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity importedOffice = QuarkusTransaction.requiringNew()
                .call(() -> stays.find("user.id = ?1 and timestamp = ?2", imported, officeStart).firstResult());
        assertThat(importedOffice.getVisitName()).isEqualTo("Desk");

        var corrections = timelineEdits.list(imported);
        assertThat(corrections).filteredOn(edit -> edit.op() == TimelineCorrectionOp.PLACE_STAY).hasSize(2);
        assertThat(corrections).filteredOn(edit -> edit.op() == TimelineCorrectionOp.SET_TRIP_TYPE)
                .as("the parent split keeps its source movement type on both legs").hasSize(2);
        assertThat(corrections).filteredOn(edit -> edit.op() == TimelineCorrectionOp.SET_LABEL).hasSize(1);
        assertThat(corrections).allSatisfy(edit -> assertThat(edit.applied()).isTrue());

        assertThatThrownBy(() -> edits.importInto(imported, legacyExport))
                .hasStackTraceContaining("no timeline edits");
        assertThat(timelineEdits.list(imported)).hasSameSizeAs(corrections);

        var coffee = corrections.stream().filter(edit -> at("08:12").equals(edit.anchorStart())).findFirst().orElseThrow();
        var bakery = corrections.stream().filter(edit -> at("08:16").equals(edit.anchorStart())).findFirst().orElseThrow();
        timelineEdits.revert(imported, bakery.id(), false);
        timelineEdits.revert(imported, coffee.id(), false);
        harness.fullRebuild(imported);
        assertThat(harness.snapshot(imported).stays())
                .as("stays after undoing both split corrections")
                .noneSatisfy(stay -> assertThat(stay).contains("'Bakery'"))
                .noneSatisfy(stay -> assertThat(stay).contains("'Coffee'"));
    }

    private static Map<String, Object> split(Instant start, Instant end, double[] place, String name,
                                             Instant sourceStart, long sourceSeconds, String movementType) {
        var split = new LinkedHashMap<String, Object>();
        split.put("stayStartTime", start.toString());
        split.put("stayEndTime", end.toString());
        split.put("stayLatitude", place[0]);
        split.put("stayLongitude", place[1]);
        split.put("stayLocationName", name);
        split.put("stayLocationNameCustom", true);
        split.put("sourceTripTimestamp", sourceStart.toString());
        split.put("sourceTripDurationSeconds", sourceSeconds);
        if (movementType != null) split.put("sourceTripMovementType", movementType);
        return split;
    }

    /** The export as an older server wrote it: corrections only in the legacy overrides file. */
    private byte[] asLegacyExport(byte[] export, Map<String, Object> overrides) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var input = new ZipInputStream(new ByteArrayInputStream(export));
             var copy = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] content = input.readAllBytes();
                if (entry.getName().equals(ExportImportConstants.FileNames.TIMELINE_EDITS)
                        || entry.getName().equals(ExportImportConstants.FileNames.TIMELINE_OVERRIDES)) {
                    continue;
                }
                copy.putNextEntry(new ZipEntry(entry.getName()));
                copy.write(content);
                copy.closeEntry();
            }
            copy.putNextEntry(new ZipEntry(ExportImportConstants.FileNames.TIMELINE_OVERRIDES));
            copy.write(json.writeValueAsBytes(overrides));
            copy.closeEntry();
        }
        return output.toByteArray();
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}

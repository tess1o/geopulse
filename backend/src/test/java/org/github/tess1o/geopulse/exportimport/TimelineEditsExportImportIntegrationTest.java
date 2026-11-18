package org.github.tess1o.geopulse.exportimport;

import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.map;
import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.set;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportFormat;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.service.ExportDataGenerator;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import jakarta.persistence.EntityManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineEditsExportImportIntegrationTest {

    private static final Instant START = Instant.parse("2026-10-21T08:00:00Z");
    private static final Instant END = Instant.parse("2026-10-21T10:00:00Z");

    @Inject TestUserFactory users;
    @Inject TimelineStayRepository stays;
    @Inject TimelineEditService edits;
    @Inject ExportDataGenerator exporter;
    @Inject ImportDataService importer;
    @Inject EntityManager entityManager;
    @Inject org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService activities;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cleanExportImportsTheOrderedEditIntoAnotherUser(boolean withBeforeValues) throws Exception {
        UUID source = users.createUser("edit-export-source").userId();
        QuarkusTransaction.requiringNew().run(() -> stays.persist(TimelineStayEntity.builder()
                .user(UserEntity.findById(source)).timestamp(START).stayDuration(7200)
                .location(GeoUtils.createPoint(30.5234, 50.4501)).locationName("Home")
                .locationSource(LocationSource.FAVORITE).build()));

        TimelineEditRequest request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_VISIT_NAME);
        request.setAnchorStart(START);
        request.setAnchorEnd(END);
        set(request, new LinkedHashMap<>(java.util.Map.of("visitName", "Exported visit")));
        edits.create(source, request);
        if (!withBeforeValues) QuarkusTransaction.requiringNew().run(() -> entityManager.createQuery(
                "update TimelineEditEntity set beforeValues = null where user.id = :userId")
                .setParameter("userId", source).executeUpdate());

        ExportDateRange range = new ExportDateRange();
        range.setStartDate(START.minusSeconds(60));
        range.setEndDate(END.plusSeconds(60));
        ExportJob exportJob = new ExportJob(source, List.of(ExportImportConstants.DataTypes.TIMELINE_EDITS),
                range, ExportFormat.GEOPULSE);
        byte[] archive = QuarkusTransaction.requiringNew().call(() -> {
            exporter.generateGeoPulseNativeExport(exportJob);
            return Files.readAllBytes(Path.of(exportJob.getTempFilePath()));
        });
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            assertThat(zip.getNextEntry().getName()).isEqualTo(ExportImportConstants.FileNames.METADATA);
            assertThat(zip.getNextEntry().getName()).isEqualTo(ExportImportConstants.FileNames.TIMELINE_EDITS);
        }

        UUID target = users.createUser("edit-export-target").userId();
        ImportOptions options = new ImportOptions();
        options.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.TIMELINE_EDITS));
        importer.processImportData(new ImportJob(target, options, "timeline-edits.zip", archive));

        assertThat(edits.list(target)).singleElement().satisfies(edit -> {
            assertThat(edit.seq()).isEqualTo(1);
            assertThat(edit.op()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionOp.SET_LABEL);
            assertThat(edit.status()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.IN_EFFECT);
            assertThat(map(edit.payload())).containsEntry("visitName", "Exported visit");
            if (withBeforeValues) {
                assertThat(map(edit.beforeValues())).containsEntry("locationName", "Home").containsEntry("start", START.toString());
            } else {
                assertThat(edit.beforeValues()).isNull();
            }
        });
    }

    /**
     * An import appends corrections with new identities in the target account: correction ids and group ids are
     * both new, while corrections saved together stay one group.
     */
    @org.junit.jupiter.api.Test
    void importedCorrectionsGetNewGroupIdsAndKeepTheirGrouping() throws Exception {
        UUID source = users.createUser("group-export-source").userId();
        var batch = activities.save(source, new org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService.Request(
                null, List.of(stay(START, START.plusSeconds(1800), "First"), stay(START.plusSeconds(1800), END, "Second")),
                null, null));
        var single = activities.save(source, new org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService.Request(
                null, List.of(stay(END.plusSeconds(600), END.plusSeconds(1800), "Third")), null, null));

        ExportDateRange range = new ExportDateRange();
        range.setStartDate(START.minusSeconds(60));
        range.setEndDate(END.plusSeconds(3600));
        ExportJob exportJob = new ExportJob(source, List.of(ExportImportConstants.DataTypes.TIMELINE_EDITS),
                range, ExportFormat.GEOPULSE);
        byte[] archive = QuarkusTransaction.requiringNew().call(() -> {
            exporter.generateGeoPulseNativeExport(exportJob);
            return Files.readAllBytes(Path.of(exportJob.getTempFilePath()));
        });
        UUID target = users.createUser("group-export-target").userId();
        ImportOptions options = new ImportOptions();
        options.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.TIMELINE_EDITS));
        importer.processImportData(new ImportJob(target, options, "timeline-edits.zip", archive));

        var imported = edits.list(target);
        assertThat(imported).hasSize(3);
        var sourceGroups = java.util.Set.of(batch.groupId(), single.groupId());
        assertThat(imported).allSatisfy(edit -> assertThat(sourceGroups).doesNotContain(edit.groupId()));
        var importedGroups = imported.stream().collect(java.util.stream.Collectors.groupingBy(
                edit -> edit.groupId(), java.util.stream.Collectors.counting()));
        assertThat(importedGroups.values()).containsExactlyInAnyOrder(2L, 1L);
    }

    private static org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService.Activity stay(
            Instant start, Instant end, String name) {
        var activity = new org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService.Activity();
        activity.setSegmentType("STAY");
        activity.setStartTime(start);
        activity.setEndTime(end);
        activity.setLatitude(50.4501);
        activity.setLongitude(30.5234);
        activity.setLocationName(name);
        return activity;
    }
}

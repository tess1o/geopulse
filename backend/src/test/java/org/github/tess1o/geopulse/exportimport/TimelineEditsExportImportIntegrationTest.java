package org.github.tess1o.geopulse.exportimport;

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
        request.setPayload(new LinkedHashMap<>(java.util.Map.of("visitName", "Exported visit")));
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
            assertThat(edit.kind()).isEqualTo(TimelineEditKind.SET_VISIT_NAME);
            assertThat(edit.status()).isEqualTo(TimelineEditStatus.ACTIVE);
            assertThat(edit.payload()).containsEntry("visitName", "Exported visit");
            if (withBeforeValues) {
                assertThat(edit.beforeValues()).containsEntry("locationName", "Home").containsEntry("start", START.toString());
            } else {
                assertThat(edit.beforeValues()).isNull();
            }
        });
    }
}

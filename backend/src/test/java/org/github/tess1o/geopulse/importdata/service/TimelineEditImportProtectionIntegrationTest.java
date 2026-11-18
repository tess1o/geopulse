package org.github.tess1o.geopulse.importdata.service;

import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.map;
import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.payload;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.dto.TimelineEditsDataDto;
import org.github.tess1o.geopulse.importdata.model.*;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.streaming.edits.*;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;
import org.github.tess1o.geopulse.testsupport.*;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineEditImportProtectionIntegrationTest {
    @Inject TestUserFactory users;
    @Inject GeoPulseImportStrategy strategy;
    @Inject GeoPulseTimelineDetailsImporter importer;
    @Inject TimelineEditService edits;
    @Inject EntityManager em;
    @Inject ObjectMapper json;
    @Inject org.github.tess1o.geopulse.export.service.ExportDataGenerator exporter;
    private static final Instant START = Instant.parse("2026-09-15T08:00:00Z");

    @Test
    void rejectsOccupiedDestinationBeforeImportingAnyOtherFiles() throws Exception {
        UUID user = users.createUser("occupied-edits").userId();
        QuarkusTransaction.requiringNew().run(() -> em.persist(TimelineEditEntity.builder()
                .user(em.getReference(UserEntity.class, user)).groupId(java.util.UUID.randomUUID())
                .seq(1).kind(TimelineEditKind.SET_VISIT_NAME)
                .anchorStart(START).anchorEnd(START.plusSeconds(3600)).affectedFrom(START).affectedTo(START.plusSeconds(3600))
                .payload(payload(TimelineEditKind.SET_VISIT_NAME, new LinkedHashMap<>(Map.of("visitName", "Keep me")))).status(TimelineEditStatus.ACTIVE).build()));
        byte[] archive;
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(ExportImportConstants.FileNames.FAVORITES));
            // This deliberately invalid file must never be parsed: edit preflight comes first.
            zip.write("invalid favorites".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry(ExportImportConstants.FileNames.TIMELINE_EDITS));
            zip.write(json.writeValueAsBytes(data())); zip.closeEntry(); zip.finish(); archive = bytes.toByteArray();
        }
        var job = job(user, archive);
        assertThatThrownBy(() -> strategy.processImportData(job)).hasMessageContaining("no timeline edits");
        assertThat(edits.list(user)).singleElement().satisfies(edit -> {
            assertThat(edit.seq()).isEqualTo(1); assertThat(map(edit.payload())).containsEntry("visitName", "Keep me");
            assertThat(edit.status()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.IN_EFFECT);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "right", "start", "end"})
    void remapsEveryPayloadReferenceAndClearsMissingIds(String prefix) throws Exception {
        UUID user = users.createUser("payload-remap").userId();
        String favoriteKey = prefix.isEmpty() ? "favoriteId" : prefix + "FavoriteId";
        String geocoding = prefix.isEmpty() ? "geocodingId" : prefix + "GeocodingId";
        var data = data();
        TimelineEditKind kind = prefix.isEmpty() ? TimelineEditKind.SET_PLACE : prefix.equals("right") ? TimelineEditKind.SPLIT_STAY : TimelineEditKind.FORCE_TRIP;
        data.getEdits().getFirst().setKind(kind);
        var supplied = new LinkedHashMap<>(validPayload(kind));
        supplied.put(favoriteKey,91L); supplied.put(geocoding,92L);
        String nameKey = prefix.isEmpty() ? "locationName" : prefix + "LocationName";
        supplied.put(nameKey,"Snapshot");
        data.getEdits().getFirst().setPayload(supplied);
        var maps = new ImportReferenceMaps();
        // Payload-only references need remapping even when no top-level association exists.
        long mappedFavorite = QuarkusTransaction.requiringNew().call(() -> {
            var favorite = org.github.tess1o.geopulse.favorites.model.FavoritesEntity.builder()
                    .user(em.getReference(UserEntity.class, user)).name("Destination place")
                    .geometry(org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(30.0, 50.0))
                    .type(org.github.tess1o.geopulse.favorites.model.FavoriteLocationType.POINT).build();
            em.persist(favorite); em.flush(); return favorite.getId();
        });
        maps.favoriteIds.put(91L, mappedFavorite);
        importer.importTimelineEditsData(json.writeValueAsBytes(data), job(user, new byte[0]), maps);
        assertThat(edits.list(user)).singleElement().satisfies(edit -> {
            assertThat(((Number) map(edit.payload()).get(favoriteKey)).longValue()).isEqualTo(mappedFavorite);
            assertThat(map(edit.payload())).doesNotContainKey(geocoding).containsEntry(nameKey, "Snapshot");
        });
    }

    @Test
    void importsAllKindsAndStatusesAndSupersessionLinksInOrder() throws Exception {
        UUID user = users.createUser("all-edits-import").userId();
        var rows = new ArrayList<TimelineEditsDataDto.EditDto>();
        long seq = 0;
        for (var kind : TimelineEditKind.values()) for (var status : TimelineEditStatus.values()) {
            rows.add(TimelineEditsDataDto.EditDto.builder().id(++seq).seq(seq).kind(kind).status(status)
                    .anchorStart(START).anchorEnd(START.plusSeconds(3600)).affectedFrom(START).affectedTo(START.plusSeconds(3600))
                    .payload(validPayload(kind)).supersededById(status == TimelineEditStatus.SUPERSEDED ? 1L : null).build());
        }
        importer.importTimelineEditsData(json.writeValueAsBytes(TimelineEditsDataDto.builder().edits(rows).build()), job(user, new byte[0]), new ImportReferenceMaps());
        var imported = edits.list(user);
        assertThat(imported).hasSize(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            assertThat(imported.get(i).op()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionOp.of(rows.get(i).getKind()));
            assertThat(imported.get(i).status()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.of(rows.get(i).getStatus()));
            assertThat(imported.get(i).seq()).isEqualTo(i + 1);
        }
        QuarkusTransaction.requiringNew().run(() -> {
            var superseded = em.createQuery("select e from TimelineEditEntity e where e.user.id = :user and e.status = :status", TimelineEditEntity.class)
                    .setParameter("user", user).setParameter("status", TimelineEditStatus.SUPERSEDED).getResultList();
            assertThat(superseded).allSatisfy(e -> assertThat(e.getSupersededBy().getSeq()).isEqualTo(1));
        });
    }

    @Test
    void snapshotRoundTripKeepsStoredVisitLabelAndEditHistoryWithoutRegenerating() throws Exception {
        UUID user = users.createUser("snapshot-edit").userId();
        Long stayId = QuarkusTransaction.requiringNew().call(() -> {
            var stay = org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity.builder()
                    .user(em.getReference(UserEntity.class, user)).timestamp(START).stayDuration(3600)
                    .location(org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(30.0, 50.0))
                    .locationName("Home").visitName("Snapshot label")
                    .locationSource(org.github.tess1o.geopulse.streaming.model.domain.LocationSource.HISTORICAL).build();
            em.persist(stay); em.flush(); return stay.getId();
        });
        var data = data(); data.getEdits().getFirst().setPayload(Map.of("visitName", "Snapshot label"));
        importer.importTimelineEditsData(json.writeValueAsBytes(data), job(user, new byte[0]), new ImportReferenceMaps());
        var range = new org.github.tess1o.geopulse.export.model.ExportDateRange();
        range.setStartDate(START.minusSeconds(60)); range.setEndDate(START.plusSeconds(3660));
        var export = new org.github.tess1o.geopulse.export.model.ExportJob(user,
                List.of(ExportImportConstants.DataTypes.TIMELINE, ExportImportConstants.DataTypes.TIMELINE_EDITS), range,
                org.github.tess1o.geopulse.export.model.ExportFormat.GEOPULSE);
        byte[] archive = QuarkusTransaction.requiringNew().call(() -> {
            exporter.generateGeoPulseNativeExport(export);
            return java.nio.file.Files.readAllBytes(java.nio.file.Path.of(export.getTempFilePath()));
        });
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("delete from TimelineEditEntity e where e.user.id = :user").setParameter("user", user).executeUpdate();
            em.createQuery("delete from TimelineStayEntity s where s.user.id = :user").setParameter("user", user).executeUpdate();
        });
        var restore = job(user, archive); restore.getOptions().setSnapshotRestore(true);
        strategy.processImportData(restore);
        assertThat(restore.getTimelineJobId()).isNull();
        QuarkusTransaction.requiringNew().run(() -> {
            var stay = em.find(org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity.class, stayId);
            assertThat(stay.getTimestamp()).isEqualTo(START); assertThat(stay.getStayDuration()).isEqualTo(3600);
            assertThat(stay.getLocationName()).isEqualTo("Home"); assertThat(stay.getVisitName()).isEqualTo("Snapshot label");
        });
        assertThat(edits.list(user)).singleElement().satisfies(edit -> {
            assertThat(edit.status()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.IN_EFFECT);
            assertThat(map(edit.payload())).containsEntry("visitName", "Snapshot label");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"movement", "instant", "label", "coordinates", "version", "route"})
    void rejectsInvalidPayloadsWithoutPersistingCorrections(String invalid) throws Exception {
        UUID user = users.createUser("invalid-correction-import").userId();
        var archive = data(); var row = archive.getEdits().getFirst();
        switch (invalid) {
            case "movement" -> { row.setKind(TimelineEditKind.SET_MOVEMENT_TYPE); row.setPayload(Map.of("movementType","DRIVING")); }
            case "instant" -> { row.setKind(TimelineEditKind.SET_TIMES); row.setPayload(Map.of("start","invalid","end",START.plusSeconds(60).toString())); }
            case "label" -> row.setPayload(Map.of("visitName","x".repeat(501)));
            case "coordinates" -> { row.setKind(TimelineEditKind.FORCE_STAY); row.setPayload(Map.of("latitude",91.0,"longitude",30.0)); }
            case "version" -> row.setPayloadVersion(2);
            case "route" -> { row.setKind(TimelineEditKind.SET_ROUTE); row.setPayload(Map.of("waypoints",List.of(Map.of("latitude",50.0,"longitude",30.0)))); }
        }
        byte[] bytes = json.writeValueAsBytes(archive);
        assertThatThrownBy(() -> importer.importTimelineEditsData(bytes,job(user,bytes),new ImportReferenceMaps())).isInstanceOf(RuntimeException.class);
        assertThat(edits.list(user)).isEmpty();
    }

    private static Map<String,Object> validPayload(TimelineEditKind kind) {
        return switch (kind) {
            case SET_MOVEMENT_TYPE -> Map.of("movementType","WALK");
            case SET_VISIT_NAME -> Map.of("visitName","Snapshot");
            case FORCE_STAY, SET_PLACE -> Map.of("latitude",50.0,"longitude",30.0,"locationName","Snapshot");
            case FORCE_TRIP -> Map.of("startLatitude",50.0,"startLongitude",30.0,"endLatitude",50.1,"endLongitude",30.1,"movementType","WALK");
            case SET_TIMES -> Map.of("start",START.toString(),"end",START.plusSeconds(1800).toString());
            case SPLIT_STAY -> Map.of("splitAt",START.plusSeconds(1800).toString(),"rightLatitude",50.0,"rightLongitude",30.0,"rightLocationName","Snapshot");
            case MERGE -> Map.of("direction","NEXT");
            case REMOVE_EVENT -> Map.of("resolution","GAP");
            case SET_ROUTE -> Map.of("waypoints",List.of(Map.of("latitude",50.0,"longitude",30.0),Map.of("latitude",50.1,"longitude",30.1)));
        };
    }

    private TimelineEditsDataDto data() {
        return TimelineEditsDataDto.builder().edits(List.of(TimelineEditsDataDto.EditDto.builder().id(1L).seq(1)
                .kind(TimelineEditKind.SET_VISIT_NAME).status(TimelineEditStatus.ACTIVE)
                .anchorStart(START).anchorEnd(START.plusSeconds(3600)).affectedFrom(START).affectedTo(START.plusSeconds(3600))
                .payload(Map.of("visitName", "Imported")).build())).build();
    }
    private ImportJob job(UUID user, byte[] archive) {
        var options = new ImportOptions(); options.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        return new ImportJob(user, options, "edits.zip", archive);
    }
}

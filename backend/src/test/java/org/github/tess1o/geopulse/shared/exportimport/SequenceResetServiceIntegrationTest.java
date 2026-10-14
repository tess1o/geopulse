package org.github.tess1o.geopulse.shared.exportimport;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every GeoPulse import ends by resetting the id sequences to the table's max id. Hibernate takes reverse
 * geocoding ids from its sequence in blocks (the sequence increments by 50; each value is the first id of a
 * block) and hands them out from memory. A reset that runs while another transaction holds saved but uncommitted
 * rows cannot see them, so it can move the sequence back into the block in use: the next block then repeats ids
 * that are already taken, and the insert that gets one fails on the primary key.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class SequenceResetServiceIntegrationTest {

    private static final String TABLE = "reverse_geocoding_location";
    private static final String SEQUENCE = "reverse_geocoding_location_seq";
    private static final String PROVIDER = "sequence-reset-race-test";

    @Inject
    SequenceResetService sequenceResetService;

    @Inject
    EntityManager entityManager;

    private TestCoordinates.Scope coordinateScope;
    private int savedCount;

    @BeforeEach
    void setUp() {
        coordinateScope = TestCoordinates.newScope();
        savedCount = 0;
    }

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createQuery("DELETE FROM ReverseGeocodingLocationEntity r WHERE r.providerName = :provider")
                .setParameter("provider", PROVIDER)
                .executeUpdate());
    }

    @Test
    @DisplayName("Resetting sequences while another transaction holds uncommitted rows does not reuse ids")
    void resetDuringUncommittedInsertsDoesNotReuseIds() {
        int blockSize = sequenceIncrement();
        Set<Long> ids = new HashSet<>();

        // Commit rows up to the third-to-last id of Hibernate's current block. With no other sequence user (tests
        // touching the database run one at a time), the sequence's last value is the first id of that block.
        long id = saveLocation();
        long blockStart = lastSequenceValue();
        while (id > blockStart + blockSize - 3) {
            id = saveLocation();
            blockStart = lastSequenceValue();
        }
        while (id < blockStart + blockSize - 3) {
            id = saveLocation();
        }
        ids.add(id);

        QuarkusTransaction.requiringNew().run(() -> {
            // An import saves rows that are not committed yet: the last two ids of this block and the first ids
            // of the next one.
            for (int i = 0; i < 10; i++) {
                ReverseGeocodingLocationEntity location = newLocation();
                entityManager.persist(location);
                ids.add(location.getId());
            }
            entityManager.flush();

            // Meanwhile another import finishes and resets the sequences. It cannot see the rows above.
            QuarkusTransaction.requiringNew().run(() -> sequenceResetService.resetSequencesForTables(TABLE));
        });

        // Use up the current block and the next one. Every save must succeed with an id not used before.
        for (int i = 0; i < blockSize * 2; i++) {
            long next = assertDoesNotThrow(this::saveLocation,
                    "Saving reverse geocoding row " + (i + 1) + " after the sequence reset failed");
            assertTrue(ids.add(next), "Id " + next + " was handed out twice after the sequence reset");
        }
    }

    @Test
    @DisplayName("Resetting sequences after an import moves them past rows imported with their original ids")
    void resetAfterImportAdvancesPastImportedIds() {
        int blockSize = sequenceIncrement();
        Set<Long> ids = new HashSet<>();
        ids.add(saveLocation());

        // An import restores a row with its original id, right where Hibernate's next block of ids would start.
        long importedId = lastSequenceValue() + blockSize;
        ids.add(importedId);
        QuarkusTransaction.requiringNew().run(() -> {
            Point point = newLocation().getRequestCoordinates();
            entityManager.createNativeQuery("""
                            INSERT INTO reverse_geocoding_location
                            (id, request_coordinates, result_coordinates, display_name, provider_name, created_at)
                            VALUES (?1, ST_SetSRID(ST_MakePoint(?2, ?3), 4326), ST_SetSRID(ST_MakePoint(?2, ?3), 4326),
                                    'Imported with original id', ?4, NOW())
                            """)
                    .setParameter(1, importedId)
                    .setParameter(2, point.getX())
                    .setParameter(3, point.getY())
                    .setParameter(4, PROVIDER)
                    .executeUpdate();
            sequenceResetService.resetSequencesForTables(TABLE);
        });

        // Use up the current block and the next one: none of them may reuse the imported id.
        for (int i = 0; i < blockSize * 2; i++) {
            long next = assertDoesNotThrow(this::saveLocation,
                    "Saving reverse geocoding row " + (i + 1) + " after importing id " + importedId + " failed");
            assertTrue(ids.add(next), "Id " + next + " was handed out twice after the sequence reset");
        }
    }

    private int sequenceIncrement() {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager
                .createNativeQuery("SELECT increment_by FROM pg_sequences WHERE sequencename = ?1")
                .setParameter(1, SEQUENCE)
                .getSingleResult()).intValue());
    }

    private long lastSequenceValue() {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager
                .createNativeQuery("SELECT last_value FROM " + SEQUENCE)
                .getSingleResult()).longValue());
    }

    private long saveLocation() {
        return QuarkusTransaction.requiringNew().call(() -> {
            ReverseGeocodingLocationEntity location = newLocation();
            entityManager.persist(location);
            return location.getId();
        });
    }

    private ReverseGeocodingLocationEntity newLocation() {
        int index = ++savedCount;
        Point point = coordinateScope.point(30.0 + index * 0.0001, 50.0);
        ReverseGeocodingLocationEntity location = new ReverseGeocodingLocationEntity();
        location.setRequestCoordinates(point);
        location.setResultCoordinates(point);
        location.setDisplayName("Sequence reset race " + index);
        location.setProviderName(PROVIDER);
        location.setCreatedAt(Instant.now());
        location.setLastAccessedAt(Instant.now());
        return location;
    }
}

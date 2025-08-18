package org.github.tess1o.geopulse.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
public class GpsPointServiceTest {

    private UUID userId;

    @Inject
    GpsPointService gpsPointService;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    public void setup() {
        userRepository.findAll().stream().forEach(user -> userRepository.delete(user));
        UserEntity user = UserEntity.builder()
                .email("test@test.com123")
                .role("USER")
                .passwordHash("pass")
                .build();
        userRepository.persist(user);
        userId = user.getId();
    }

    @Test
    @Transactional
    public void testSaveOwnTracksGpsPoint() {
        Integer tst = (int)Instant.now().plusSeconds(20000).toEpochMilli() / 1000;
        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .type("location")
                .acc(0.2)
                .lat(40.0)
                .lon(-74.0)
                .tst(tst)
                .vel(5.0)
                .build();

        Statistics stats = getStatistics();
        stats.clear();  // Reset stats before operation

        gpsPointService.saveOwnTracksGpsPoint(message, userId, "test-device", GpsSourceType.OWNTRACKS);

        var insertCount = stats.getEntityInsertCount();
        assertEquals(1, insertCount); // Expect 1 query

        var selectCount = stats.getQueryExecutionCount();
        assertTrue(selectCount <= 2); //for duplication check and possbile timeline_regeneration_queue

        assertEquals(1, gpsPointRepository.count());
        GpsPointEntity savedGpsPoint = gpsPointRepository.findAll().firstResult();
        assertEquals(userId, savedGpsPoint.getUser().getId());
        assertFalse(Hibernate.isInitialized(savedGpsPoint.getUser()));
        assertNull(savedGpsPoint.getAltitude());
        assertNull(savedGpsPoint.getBattery());
        assertEquals(0.2, savedGpsPoint.getAccuracy(), 0.000001);
        assertEquals(40.0, savedGpsPoint.getCoordinates().getY(), 0.000001);
        assertEquals(-74.0, savedGpsPoint.getCoordinates().getX(), 0.000001);
        assertEquals(5.0, savedGpsPoint.getVelocity(), 0.000001);
        assertEquals("test-device", savedGpsPoint.getDeviceId());
        assertEquals(tst, (int)savedGpsPoint.getTimestamp().getEpochSecond());
    }


    private Statistics getStatistics() {
        Session session = em.unwrap(Session.class);
        SessionFactory sessionFactory = session.getSessionFactory();
        return sessionFactory.getStatistics();
    }

}
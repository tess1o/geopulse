package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;
import org.github.tess1o.geopulse.streaming.model.dto.CreateTimelineRegenerationCampaignRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignPreviewDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignPreviewRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignSummaryDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignSource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignStatus;
import org.github.tess1o.geopulse.streaming.repository.TimelineRegenerationCampaignRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineRegenerationCampaignUserRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineRegenerationCampaignServiceTest {

    @Inject
    TimelineRegenerationCampaignService campaignService;

    @Inject
    TimelineRegenerationCampaignRepository campaignRepository;

    @Inject
    TimelineRegenerationCampaignUserRepository campaignUserRepository;

    @Inject
    UserNotificationRepository notificationRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private UserEntity adminUser;
    private UserEntity eligibleUser;
    private UserEntity unaffectedUser;

    @BeforeEach
    @Transactional
    void setUp() {
        adminUser = createUser("timeline-campaign-admin");
        eligibleUser = createUser("timeline-campaign-eligible");
        unaffectedUser = createUser("timeline-campaign-unaffected");

        userRepository.persist(adminUser);
        userRepository.persist(eligibleUser);
        userRepository.persist(unaffectedUser);

        createGpsPoint(eligibleUser, Instant.parse("2035-07-12T12:00:00Z"));
        createGpsPoint(unaffectedUser, Instant.parse("2035-07-11T23:59:59Z"));
    }

    @Test
    @Transactional
    void previewAdminCampaignCountsAffectedUsersWithoutPersistingAnything() {
        long campaignCount = campaignRepository.count();
        long campaignUserCount = campaignUserRepository.count();
        long notificationCount = notificationRepository.count();

        UserEntity previewUser = createUser("timeline-campaign-preview");
        userRepository.persist(previewUser);
        createGpsPoint(previewUser, Instant.parse("2045-01-02T00:00:00Z"));
        gpsPointRepository.flush();

        TimelineRegenerationCampaignPreviewRequest request = new TimelineRegenerationCampaignPreviewRequest();
        request.setAffectedFrom(Instant.parse("2045-01-02T00:00:00Z"));

        TimelineRegenerationCampaignPreviewDTO preview = campaignService.previewAdminCampaign(request);

        assertThat(preview.getAffectedFrom()).isEqualTo(Instant.parse("2045-01-02T00:00:00Z"));
        assertThat(preview.getAffectedUsers()).isEqualTo(1);
        assertThat(campaignRepository.count()).isEqualTo(campaignCount);
        assertThat(campaignUserRepository.count()).isEqualTo(campaignUserCount);
        assertThat(notificationRepository.count()).isEqualTo(notificationCount);
    }

    @Test
    void createAdminCampaignRequiresReason() {
        CreateTimelineRegenerationCampaignRequest request = request("blank-reason-campaign", " ");

        assertThatThrownBy(() -> campaignService.createAdminCampaign(request, adminUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    void createAdminCampaignEnqueuesEligibleUsersAndPublishesDedupedNotification() {
        String campaignKey = TestIds.uniqueEmail("campaign-key").replace("@example.com", "");
        CreateTimelineRegenerationCampaignRequest request = request(campaignKey, "A timeline repair is required.");

        TimelineRegenerationCampaignSummaryDTO created = campaignService.createAdminCampaign(request, adminUser.getId());
        campaignService.reconcileActiveCampaigns();

        assertThat(created.getCampaignKey()).isEqualTo(campaignKey);
        assertThat(created.getSource()).isEqualTo(TimelineRegenerationCampaignSource.ADMIN);
        assertThat(created.getTotalUsers()).isEqualTo(1);
        assertThat(created.getPendingUsers()).isEqualTo(1);
        assertThat(campaignService.hasActiveCampaignForUser(eligibleUser.getId())).isTrue();
        assertThat(campaignService.hasActiveCampaignForUser(unaffectedUser.getId())).isFalse();

        var campaign = campaignRepository.findByCampaignKey(campaignKey).orElseThrow();
        var campaignUsers = campaignUserRepository.findByCampaign(campaign.getId());
        assertThat(campaignUsers).hasSize(1);
        assertThat(campaignUsers.get(0).getUser().getId()).isEqualTo(eligibleUser.getId());

        var notifications = notificationRepository.findByOwner(eligibleUser.getId(), 20).stream()
                .filter(notification -> notification.getSource() == NotificationSource.TIMELINE)
                .filter(notification -> notification.getType() == NotificationType.TIMELINE_REGENERATION_REQUIRED)
                .filter(notification -> campaign.getId().toString().equals(notification.getObjectRef()))
                .toList();

        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getTitle()).isEqualTo("Timeline refresh scheduled");
        assertThat(notifications.get(0).getMessage()).isEqualTo("A timeline repair is required.");
        assertThat(notifications.get(0).getDedupeKey())
                .isEqualTo("timeline-regeneration-campaign:" + campaign.getId() + ":user:" + eligibleUser.getId());
    }

    @Test
    void createAdminCampaignCompletesImmediatelyWhenNoUsersHaveDataAfterCutoff() {
        String campaignKey = TestIds.uniqueEmail("empty-campaign-key").replace("@example.com", "");
        CreateTimelineRegenerationCampaignRequest request = request(campaignKey, "No eligible users repair.");
        request.setAffectedFrom(Instant.parse("2040-01-01T00:00:00Z"));

        TimelineRegenerationCampaignSummaryDTO created = campaignService.createAdminCampaign(request, adminUser.getId());

        assertThat(created.getTotalUsers()).isZero();
        assertThat(created.getStatus()).isEqualTo(TimelineRegenerationCampaignStatus.COMPLETED);
    }

    private CreateTimelineRegenerationCampaignRequest request(String campaignKey, String reason) {
        CreateTimelineRegenerationCampaignRequest request = new CreateTimelineRegenerationCampaignRequest();
        request.setCampaignKey(campaignKey);
        request.setAffectedFrom(Instant.parse("2035-07-12T00:00:00Z"));
        request.setReason(reason);
        return request;
    }

    private UserEntity createUser(String prefix) {
        UserEntity user = new UserEntity();
        user.setEmail(TestIds.uniqueEmail(prefix));
        user.setFullName(prefix);
        user.setPasswordHash("test");
        user.setActive(true);
        return user;
    }

    private void createGpsPoint(UserEntity user, Instant timestamp) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(user);
        point.setTimestamp(timestamp);
        point.setCoordinates(geometryFactory.createPoint(new Coordinate(30.5234, 50.4501)));
        point.setAccuracy(5.0);
        point.setVelocity(0.0);
        gpsPointRepository.persist(point);
    }
}

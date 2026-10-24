package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignUserStatus;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #635: a campaign user that can no longer be claimed must not stay PENDING, because a PENDING campaign user
 * stops the real-time timeline of that user.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineRegenerationCampaignStuckUserIntegrationTest {

    @Inject
    TimelineRegenerationCampaignService campaignService;

    @Inject
    TimelineJobProgressService jobProgressService;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "geopulse.timeline.regeneration-campaign.max-attempts", defaultValue = "5")
    int maxAttempts;

    private UUID userId;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        userId = QuarkusTransaction.requiringNew().call(() -> {
            UserEntity user = new UserEntity();
            user.setEmail(TestIds.uniqueEmail("campaign-stuck"));
            user.setFullName("campaign-stuck");
            user.setPasswordHash("test");
            user.setActive(true);
            userRepository.persist(user);
            return user.getId();
        });
        campaignId = UUID.randomUUID();
        // The oldest active campaign is claimed first, so this campaign's user is the one the tests process.
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                        INSERT INTO timeline_regeneration_campaigns (id, campaign_key, affected_from, reason, source, created_at)
                        VALUES (:id, :key, '1970-01-01 00:00:00+00', 'test campaign', 'ADMIN', '2000-01-01 00:00:00+00')
                        """)
                .setParameter("id", campaignId)
                .setParameter("key", "stuck-user-" + campaignId)
                .executeUpdate());
    }

    @AfterEach
    void tearDown() {
        // A campaign left active would be claimed before the next test's campaign.
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                        "UPDATE timeline_regeneration_campaigns SET status = 'CANCELLED' WHERE id = :id")
                .setParameter("id", campaignId)
                .executeUpdate());
    }

    @Test
    void userBusyWithAnotherJobDoesNotUseUpItsAttempts() {
        insertCampaignUser("PENDING", 0, null, null);
        UUID otherJobId = jobProgressService.createJob(userId);

        for (int i = 0; i < maxAttempts; i++) {
            makeDue();
            assertThat(campaignService.processNextDueCampaignUser()).isTrue();
            assertThat(campaignUserStatus()).isEqualTo(TimelineRegenerationCampaignUserStatus.PENDING);
        }

        assertThat(campaignUserAttempts())
                .as("waiting for another job is not a failed attempt, so the user stays claimable")
                .isLessThan(maxAttempts);
        assertThat(campaignService.getCampaignDetails(campaignId).getInProgressUsers())
                .anyMatch(waiting -> waiting.getUserId().equals(userId)
                        && waiting.getLastError().contains("already active"));

        jobProgressService.completeJob(otherJobId);
        makeDue();
        assertThat(campaignService.processNextDueCampaignUser()).isTrue();
        assertThat(campaignUserStatus()).isEqualTo(TimelineRegenerationCampaignUserStatus.COMPLETED);
        assertThat(campaignService.hasActiveCampaignForUser(userId)).isFalse();
    }

    @Test
    void pendingUserWithoutAttemptsLeftIsMarkedFailedAndCanBeRetried() {
        insertCampaignUser("PENDING", maxAttempts, null, null);

        campaignService.reconcileActiveCampaigns();

        assertThat(campaignUserStatus()).isEqualTo(TimelineRegenerationCampaignUserStatus.FAILED);
        assertThat(campaignService.getCampaignDetails(campaignId).getFailedUsers())
                .anyMatch(failed -> failed.getUserId().equals(userId));

        campaignService.retryFailedUsers(campaignId);

        assertThat(campaignUserStatus()).isEqualTo(TimelineRegenerationCampaignUserStatus.PENDING);
        assertThat(campaignUserAttempts()).isZero();
    }

    @Test
    void interruptedRunOnTheLastAttemptIsMarkedFailed() {
        insertCampaignUser("RUNNING", maxAttempts, null, Instant.now().minus(Duration.ofHours(3)));

        campaignService.recoverStaleRunningCampaignUsers();

        assertThat(campaignUserStatus()).isEqualTo(TimelineRegenerationCampaignUserStatus.FAILED);
    }

    @Test
    void interruptedRunWithAttemptsLeftIsQueuedAgain() {
        insertCampaignUser("RUNNING", 1, UUID.randomUUID(), Instant.now().minus(Duration.ofHours(3)));

        campaignService.recoverStaleRunningCampaignUsers();

        assertThat(campaignUserStatus()).isEqualTo(TimelineRegenerationCampaignUserStatus.PENDING);
        assertThat(campaignUserAttempts()).isEqualTo(1);
    }

    @Test
    void longRunWhoseJobIsStillActiveIsNotRecovered() {
        UUID jobId = jobProgressService.createJob(userId);
        try {
            insertCampaignUser("RUNNING", 1, jobId, Instant.now().minus(Duration.ofHours(3)));

            campaignService.recoverStaleRunningCampaignUsers();

            assertThat(campaignUserStatus()).isEqualTo(TimelineRegenerationCampaignUserStatus.RUNNING);
        } finally {
            jobProgressService.completeJob(jobId);
        }
    }

    @Test
    void failedCampaignUserDoesNotHoldBackTheRealTimeTimeline() {
        insertCampaignUser("FAILED", maxAttempts, null, null);

        assertThat(campaignService.hasActiveCampaignForUser(userId)).isFalse();
    }

    private void insertCampaignUser(String status, int attempts, UUID jobId, Instant claimedAt) {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                        INSERT INTO timeline_regeneration_campaign_users (campaign_id, user_id, status, attempts, job_id, claimed_at)
                        VALUES (:campaignId, :userId, :status, :attempts, :jobId, :claimedAt)
                        """)
                .setParameter("campaignId", campaignId)
                .setParameter("userId", userId)
                .setParameter("status", status)
                .setParameter("attempts", attempts)
                .setParameter("jobId", jobId)
                .setParameter("claimedAt", claimedAt)
                .executeUpdate());
    }

    private void makeDue() {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                        UPDATE timeline_regeneration_campaign_users
                        SET next_attempt_at = NULL
                        WHERE campaign_id = :campaignId
                          AND user_id = :userId
                        """)
                .setParameter("campaignId", campaignId)
                .setParameter("userId", userId)
                .executeUpdate());
    }

    private TimelineRegenerationCampaignUserStatus campaignUserStatus() {
        return QuarkusTransaction.requiringNew().call(() -> TimelineRegenerationCampaignUserStatus.valueOf(
                entityManager.createNativeQuery("""
                                SELECT status
                                FROM timeline_regeneration_campaign_users
                                WHERE campaign_id = :campaignId
                                  AND user_id = :userId
                                """)
                        .setParameter("campaignId", campaignId)
                        .setParameter("userId", userId)
                        .getSingleResult()
                        .toString()));
    }

    private int campaignUserAttempts() {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery("""
                        SELECT attempts
                        FROM timeline_regeneration_campaign_users
                        WHERE campaign_id = :campaignId
                          AND user_id = :userId
                        """)
                .setParameter("campaignId", campaignId)
                .setParameter("userId", userId)
                .getSingleResult()).intValue());
    }
}

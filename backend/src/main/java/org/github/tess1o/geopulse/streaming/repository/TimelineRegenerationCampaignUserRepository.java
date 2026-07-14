package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.streaming.model.TimelineRegenerationCampaignClaim;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignUserEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignUserStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TimelineRegenerationCampaignUserRepository implements PanacheRepository<TimelineRegenerationCampaignUserEntity> {

    public List<TimelineRegenerationCampaignUserEntity> findByCampaign(UUID campaignId) {
        return find("campaign.id = ?1 order by createdAt asc", campaignId).list();
    }

    public List<TimelineRegenerationCampaignUserEntity> findFailuresByCampaign(UUID campaignId) {
        return find("campaign.id = ?1 and status = ?2 order by updatedAt desc",
                campaignId,
                TimelineRegenerationCampaignUserStatus.FAILED).list();
    }

    public boolean hasActiveCampaignForUser(UUID userId) {
        return count("""
                        user.id = ?1
                        and campaign.status = org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignStatus.ACTIVE
                        and status in ?2
                        """,
                userId,
                List.of(
                        TimelineRegenerationCampaignUserStatus.PENDING,
                        TimelineRegenerationCampaignUserStatus.RUNNING,
                        TimelineRegenerationCampaignUserStatus.FAILED
                )) > 0;
    }

    @Transactional
    public Optional<TimelineRegenerationCampaignClaim> claimNextDueUser(int maxAttempts) {
        Query claimQuery = getEntityManager().createNativeQuery("""
                WITH candidate AS (
                    SELECT cu.id
                    FROM timeline_regeneration_campaign_users cu
                    JOIN timeline_regeneration_campaigns c ON c.id = cu.campaign_id
                    JOIN users u ON u.id = cu.user_id
                    WHERE c.status = 'ACTIVE'
                      AND cu.status IN ('PENDING', 'FAILED')
                      AND cu.attempts < :maxAttempts
                      AND (cu.next_attempt_at IS NULL OR cu.next_attempt_at <= NOW())
                      AND u.timeline_status = 'IDLE'
                    ORDER BY c.created_at ASC, cu.created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE timeline_regeneration_campaign_users cu
                SET status = 'RUNNING',
                    attempts = cu.attempts + 1,
                    claimed_at = NOW(),
                    started_at = COALESCE(cu.started_at, NOW()),
                    completed_at = NULL,
                    last_error = NULL,
                    next_attempt_at = NULL,
                    updated_at = NOW()
                FROM candidate
                WHERE cu.id = candidate.id
                RETURNING cu.id
                """);
        claimQuery.setParameter("maxAttempts", maxAttempts);

        @SuppressWarnings("unchecked")
        List<Object> rows = claimQuery.getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Long campaignUserId = ((Number) rows.get(0)).longValue();
        return findClaimById(campaignUserId);
    }

    public Optional<TimelineRegenerationCampaignClaim> findClaimById(Long campaignUserId) {
        Query query = getEntityManager().createNativeQuery("""
                SELECT cu.id,
                       c.id,
                       c.campaign_key,
                       cu.user_id,
                       c.affected_from,
                       cu.attempts
                FROM timeline_regeneration_campaign_users cu
                JOIN timeline_regeneration_campaigns c ON c.id = cu.campaign_id
                WHERE cu.id = :campaignUserId
                """);
        query.setParameter("campaignUserId", campaignUserId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = rows.get(0);
        return Optional.of(new TimelineRegenerationCampaignClaim(
                ((Number) row[0]).longValue(),
                toUuid(row[1]),
                (String) row[2],
                toUuid(row[3]),
                TimestampUtils.getInstantSafe(row[4]),
                ((Number) row[5]).intValue()
        ));
    }

    @Transactional
    public void assignJobId(Long campaignUserId, UUID jobId) {
        update("jobId = ?1, updatedAt = ?2 where id = ?3", jobId, Instant.now(), campaignUserId);
    }

    @Transactional
    public void markCompleted(Long campaignUserId) {
        update("status = ?1, completedAt = ?2, updatedAt = ?2, lastError = null, nextAttemptAt = null where id = ?3",
                TimelineRegenerationCampaignUserStatus.COMPLETED,
                Instant.now(),
                campaignUserId);
    }

    @Transactional
    public void markRetry(Long campaignUserId, String errorMessage, Instant nextAttemptAt) {
        update("status = ?1, lastError = ?2, nextAttemptAt = ?3, updatedAt = ?4 where id = ?5",
                TimelineRegenerationCampaignUserStatus.PENDING,
                truncateError(errorMessage),
                nextAttemptAt,
                Instant.now(),
                campaignUserId);
    }

    @Transactional
    public void markFailed(Long campaignUserId, String errorMessage) {
        update("status = ?1, lastError = ?2, completedAt = ?3, updatedAt = ?3 where id = ?4",
                TimelineRegenerationCampaignUserStatus.FAILED,
                truncateError(errorMessage),
                Instant.now(),
                campaignUserId);
    }

    @Transactional
    public int recoverStaleRunning(Instant cutoff) {
        return update("""
                        status = ?1,
                        lastError = ?2,
                        nextAttemptAt = null,
                        updatedAt = ?3
                        where status = ?4
                        and claimedAt < ?5
                        """,
                TimelineRegenerationCampaignUserStatus.PENDING,
                "Recovered stale running campaign user after backend restart",
                Instant.now(),
                TimelineRegenerationCampaignUserStatus.RUNNING,
                cutoff);
    }

    @Transactional
    public long retryFailed(UUID campaignId) {
        return update("""
                        status = ?1,
                        attempts = 0,
                        jobId = null,
                        lastError = null,
                        nextAttemptAt = null,
                        completedAt = null,
                        updatedAt = ?2
                        where campaign.id = ?3
                        and status = ?4
                        """,
                TimelineRegenerationCampaignUserStatus.PENDING,
                Instant.now(),
                campaignId,
                TimelineRegenerationCampaignUserStatus.FAILED);
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private String truncateError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() <= 2000 ? errorMessage : errorMessage.substring(0, 2000);
    }
}

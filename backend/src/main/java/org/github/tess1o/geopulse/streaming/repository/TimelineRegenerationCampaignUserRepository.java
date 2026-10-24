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

    /**
     * Users that are running or waiting for another attempt, so an admin can see why a campaign has not finished.
     */
    public List<TimelineRegenerationCampaignUserEntity> findInProgressByCampaign(UUID campaignId) {
        return find("""
                        campaign.id = ?1
                        and (status = ?2 or (status = ?3 and (attempts > 0 or lastError is not null)))
                        order by updatedAt desc
                        """,
                campaignId,
                TimelineRegenerationCampaignUserStatus.RUNNING,
                TimelineRegenerationCampaignUserStatus.PENDING).list();
    }

    /**
     * A failed user is not counted: it waits for an admin retry, and holding back its real-time timeline until
     * then would leave the user without new stays and trips.
     */
    public boolean hasActiveCampaignForUser(UUID userId) {
        return count("""
                        user.id = ?1
                        and campaign.status = org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignStatus.ACTIVE
                        and status in ?2
                        """,
                userId,
                List.of(
                        TimelineRegenerationCampaignUserStatus.PENDING,
                        TimelineRegenerationCampaignUserStatus.RUNNING
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

    /**
     * Puts a claimed user back in the queue because another timeline generation holds it. Waiting is not a failure,
     * so the attempt the claim counted is given back.
     */
    @Transactional
    public void deferBusy(Long campaignUserId, String reason, Instant nextAttemptAt) {
        getEntityManager().createNativeQuery("""
                        UPDATE timeline_regeneration_campaign_users
                        SET status = 'PENDING',
                            attempts = GREATEST(attempts - 1, 0),
                            last_error = :reason,
                            next_attempt_at = :nextAttemptAt,
                            updated_at = NOW()
                        WHERE id = :id
                        """)
                .setParameter("reason", truncateError(reason))
                .setParameter("nextAttemptAt", nextAttemptAt)
                .setParameter("id", campaignUserId)
                .executeUpdate();
    }

    @Transactional
    public void markFailed(Long campaignUserId, String errorMessage) {
        update("status = ?1, lastError = ?2, completedAt = ?3, updatedAt = ?3 where id = ?4",
                TimelineRegenerationCampaignUserStatus.FAILED,
                truncateError(errorMessage),
                Instant.now(),
                campaignUserId);
    }

    public List<RunningCampaignUser> findRunningClaimedBefore(Instant cutoff) {
        return find("status = ?1 and claimedAt < ?2", TimelineRegenerationCampaignUserStatus.RUNNING, cutoff)
                .stream()
                .map(entity -> new RunningCampaignUser(entity.getId(), entity.getJobId()))
                .toList();
    }

    /**
     * Requeues users whose run was interrupted, or fails them when that run was their last attempt, so no user is
     * left RUNNING or PENDING without a way forward.
     */
    @Transactional
    public int recoverInterrupted(List<Long> campaignUserIds, int maxAttempts) {
        if (campaignUserIds.isEmpty()) {
            return 0;
        }
        return getEntityManager().createNativeQuery("""
                        UPDATE timeline_regeneration_campaign_users
                        SET status = CASE WHEN attempts >= :maxAttempts THEN 'FAILED' ELSE 'PENDING' END,
                            last_error = 'Timeline regeneration was interrupted, for example by a backend restart',
                            next_attempt_at = NULL,
                            completed_at = CASE WHEN attempts >= :maxAttempts THEN NOW() END,
                            updated_at = NOW()
                        WHERE id IN (:ids)
                          AND status = 'RUNNING'
                        """)
                .setParameter("maxAttempts", maxAttempts)
                .setParameter("ids", campaignUserIds)
                .executeUpdate();
    }

    /**
     * Fails pending users that can no longer be claimed because they have used all their attempts. Earlier versions
     * left such users PENDING, which kept their campaign active and their real-time timeline on hold for good.
     */
    @Transactional
    public int failExhaustedPending(int maxAttempts) {
        return getEntityManager().createNativeQuery("""
                        UPDATE timeline_regeneration_campaign_users cu
                        SET status = 'FAILED',
                            last_error = COALESCE(cu.last_error, 'No attempts left'),
                            next_attempt_at = NULL,
                            completed_at = NOW(),
                            updated_at = NOW()
                        FROM timeline_regeneration_campaigns c
                        WHERE c.id = cu.campaign_id
                          AND c.status = 'ACTIVE'
                          AND cu.status = 'PENDING'
                          AND cu.attempts >= :maxAttempts
                        """)
                .setParameter("maxAttempts", maxAttempts)
                .executeUpdate();
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

    public record RunningCampaignUser(Long id, UUID jobId) {
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

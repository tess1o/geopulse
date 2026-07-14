package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.insight.service.BadgeRecalculationService;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;
import org.github.tess1o.geopulse.streaming.model.TimelineRegenerationCampaignClaim;
import org.github.tess1o.geopulse.streaming.model.dto.CreateTimelineRegenerationCampaignRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignDetailDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignPreviewDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignPreviewRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignSummaryDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignUserDTO;
import org.github.tess1o.geopulse.streaming.model.entity.*;
import org.github.tess1o.geopulse.streaming.repository.TimelineRegenerationCampaignRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineRegenerationCampaignUserRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class TimelineRegenerationCampaignService {

    private static final String NOTIFICATION_TITLE = "Timeline regeneration scheduled";
    private static final String NOTIFICATION_TARGET_ROUTE = "/app/timeline/jobs";
    private static final Duration STALE_RUNNING_AFTER = Duration.ofHours(2);

    @Inject
    TimelineRegenerationCampaignRepository campaignRepository;

    @Inject
    TimelineRegenerationCampaignUserRepository campaignUserRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    TimelineJobProgressService jobProgressService;

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    BadgeRecalculationService badgeRecalculationService;

    @Transactional
    public void onStartup(@Observes StartupEvent startupEvent) {
        recoverStaleRunningCampaignUsers();
        reconcileActiveCampaigns();
    }

    @Transactional
    public TimelineRegenerationCampaignPreviewDTO previewAdminCampaign(TimelineRegenerationCampaignPreviewRequest request) {
        validatePreviewRequest(request);
        return TimelineRegenerationCampaignPreviewDTO.builder()
                .affectedFrom(request.getAffectedFrom())
                .affectedUsers(countAffectedUsers(request.getAffectedFrom()))
                .build();
    }

    @Transactional
    public TimelineRegenerationCampaignSummaryDTO createAdminCampaign(CreateTimelineRegenerationCampaignRequest request, UUID adminUserId) {
        validateCreateRequest(request);

        String campaignKey = request.getCampaignKey().trim();
        if (campaignRepository.findByCampaignKey(campaignKey).isPresent()) {
            throw new IllegalArgumentException("Timeline regeneration campaign key already exists");
        }

        UserEntity createdBy = adminUserId == null ? null : userRepository.findById(adminUserId);
        TimelineRegenerationCampaignEntity campaign = TimelineRegenerationCampaignEntity.builder()
                .campaignKey(campaignKey)
                .affectedFrom(request.getAffectedFrom())
                .reason(request.getReason().trim())
                .source(TimelineRegenerationCampaignSource.ADMIN)
                .status(TimelineRegenerationCampaignStatus.ACTIVE)
                .createdBy(createdBy)
                .build();

        campaignRepository.persist(campaign);
        campaignRepository.flush();

        reconcileCampaign(campaign);
        return toSummary(campaign);
    }

    @Transactional
    public List<TimelineRegenerationCampaignSummaryDTO> listCampaigns() {
        return campaignRepository.findAllByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public TimelineRegenerationCampaignDetailDTO getCampaignDetails(UUID campaignId) {
        TimelineRegenerationCampaignEntity campaign = campaignRepository.findByIdOptional(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Timeline regeneration campaign not found"));

        List<TimelineRegenerationCampaignUserDTO> failedUsers = campaignUserRepository.findFailuresByCampaign(campaignId)
                .stream()
                .map(this::toUserDto)
                .toList();

        return TimelineRegenerationCampaignDetailDTO.builder()
                .campaign(toSummary(campaign))
                .failedUsers(failedUsers)
                .build();
    }

    @Transactional
    public TimelineRegenerationCampaignSummaryDTO retryFailedUsers(UUID campaignId) {
        TimelineRegenerationCampaignEntity campaign = campaignRepository.findByIdOptional(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Timeline regeneration campaign not found"));

        if (campaign.getStatus() == TimelineRegenerationCampaignStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled campaigns cannot be retried");
        }

        long retried = campaignUserRepository.retryFailed(campaignId);
        if (retried > 0) {
            campaignRepository.reactivate(campaignId);
            campaign.setStatus(TimelineRegenerationCampaignStatus.ACTIVE);
            campaign.setCompletedAt(null);
            log.info("Retried {} failed timeline regeneration campaign user(s) for campaign {}", retried, campaignId);
        }

        refreshCampaignCounters(campaignId);
        return toSummary(campaign);
    }

    @Transactional
    public void reconcileActiveCampaigns() {
        for (TimelineRegenerationCampaignEntity campaign : campaignRepository.findActiveCampaigns()) {
            reconcileCampaign(campaign);
        }
    }

    public boolean processNextDueCampaignUser(int maxAttempts) {
        Optional<TimelineRegenerationCampaignClaim> claim = campaignUserRepository.claimNextDueUser(maxAttempts);
        if (claim.isEmpty()) {
            return false;
        }

        TimelineRegenerationCampaignClaim work = claim.get();
        UUID jobId = null;

        try {
            if (jobProgressService.getUserActiveJob(work.userId()).isPresent()) {
                campaignUserRepository.markRetry(
                        work.campaignUserId(),
                        "Another timeline generation job is already active for this user",
                        nextAttemptAt(work.attempts())
                );
                return true;
            }

            jobId = jobProgressService.createJob(work.userId());
            campaignUserRepository.assignJobId(work.campaignUserId(), jobId);
            jobProgressService.updateProgress(jobId, "Forced timeline regeneration queued", 1, 1,
                    Map.of(
                            "campaignId", work.campaignId().toString(),
                            "campaignKey", work.campaignKey(),
                            "affectedFrom", work.affectedFrom().toString()
                    ));

            log.info("Processing forced timeline regeneration campaign {} for user {} from {}",
                    work.campaignKey(), work.userId(), work.affectedFrom());

            timelineGenerationService.generateTimelineFromTimestamp(work.userId(), work.affectedFrom(), jobId);
            finishTimelineJob(jobId, work.userId());
            campaignUserRepository.markCompleted(work.campaignUserId());
            refreshCampaignCounters(work.campaignId());
            markCampaignCompletedIfFinished(work.campaignId());
        } catch (TimelineGenerationLockException | IllegalStateException e) {
            failOrRetry(work, jobId, maxAttempts, e.getMessage());
        } catch (Exception e) {
            log.error("Forced timeline regeneration campaign {} failed for user {}",
                    work.campaignKey(), work.userId(), e);
            failOrRetry(work, jobId, maxAttempts, e.getMessage());
        }

        return true;
    }

    public boolean hasActiveCampaignForUser(UUID userId) {
        return campaignUserRepository.hasActiveCampaignForUser(userId);
    }

    public void recoverStaleRunningCampaignUsers() {
        Instant cutoff = Instant.now().minus(STALE_RUNNING_AFTER);
        int recovered = campaignUserRepository.recoverStaleRunning(cutoff);
        if (recovered > 0) {
            log.warn("Recovered {} stale forced timeline regeneration campaign user(s)", recovered);
        }
    }

    private void validatePreviewRequest(TimelineRegenerationCampaignPreviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Preview request is required");
        }
        if (request.getAffectedFrom() == null) {
            throw new IllegalArgumentException("affectedFrom is required");
        }
    }

    private void validateCreateRequest(CreateTimelineRegenerationCampaignRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Campaign request is required");
        }
        if (request.getCampaignKey() == null || request.getCampaignKey().trim().isEmpty()) {
            throw new IllegalArgumentException("campaignKey is required");
        }
        if (request.getCampaignKey().trim().length() > 120) {
            throw new IllegalArgumentException("campaignKey cannot exceed 120 characters");
        }
        if (request.getAffectedFrom() == null) {
            throw new IllegalArgumentException("affectedFrom is required");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("reason is required");
        }
    }

    private int countAffectedUsers(Instant affectedFrom) {
        Query query = entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM users u
                WHERE EXISTS (
                    SELECT 1
                    FROM gps_points gp
                    WHERE gp.user_id = u.id
                      AND gp.timestamp >= :affectedFrom
                )
                """);
        query.setParameter("affectedFrom", affectedFrom);
        return toInt(query.getSingleResult());
    }

    private void reconcileCampaign(TimelineRegenerationCampaignEntity campaign) {
        int insertedUsers = enqueueEligibleUsers(campaign);
        int insertedNotifications = publishNotifications(campaign);
        refreshCampaignCounters(campaign.getId());
        markCampaignCompletedIfFinished(campaign.getId());

        if (insertedUsers > 0 || insertedNotifications > 0) {
            log.info("Reconciled timeline regeneration campaign {}: {} user(s), {} notification(s)",
                    campaign.getCampaignKey(), insertedUsers, insertedNotifications);
        }
    }

    private int enqueueEligibleUsers(TimelineRegenerationCampaignEntity campaign) {
        Query query = entityManager.createNativeQuery("""
                INSERT INTO timeline_regeneration_campaign_users (
                    campaign_id,
                    user_id,
                    status,
                    created_at,
                    updated_at
                )
                SELECT :campaignId,
                       u.id,
                       'PENDING',
                       NOW(),
                       NOW()
                FROM users u
                WHERE EXISTS (
                    SELECT 1
                    FROM gps_points gp
                    WHERE gp.user_id = u.id
                      AND gp.timestamp >= :affectedFrom
                )
                ON CONFLICT (campaign_id, user_id) DO NOTHING
                """);
        query.setParameter("campaignId", campaign.getId());
        query.setParameter("affectedFrom", campaign.getAffectedFrom());
        return query.executeUpdate();
    }

    private int publishNotifications(TimelineRegenerationCampaignEntity campaign) {
        Query query = entityManager.createNativeQuery("""
                INSERT INTO user_notifications (
                    owner_user_id,
                    source,
                    type,
                    title,
                    message,
                    occurred_at,
                    object_ref,
                    metadata,
                    dedupe_key,
                    created_at
                )
                SELECT cu.user_id,
                       'TIMELINE',
                       'TIMELINE_REGENERATION_REQUIRED',
                       :title,
                       c.reason,
                       NOW(),
                       c.id::TEXT,
                       jsonb_build_object(
                           'campaignId', c.id::TEXT,
                           'campaignKey', c.campaign_key,
                           'affectedFrom', c.affected_from,
                           'targetRoute', :targetRoute
                       ),
                       CONCAT('timeline-regeneration-campaign:', c.id::TEXT, ':user:', cu.user_id::TEXT),
                       NOW()
                FROM timeline_regeneration_campaign_users cu
                JOIN timeline_regeneration_campaigns c ON c.id = cu.campaign_id
                WHERE c.id = :campaignId
                ON CONFLICT (dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING
                """);
        query.setParameter("campaignId", campaign.getId());
        query.setParameter("title", NOTIFICATION_TITLE);
        query.setParameter("targetRoute", NOTIFICATION_TARGET_ROUTE);
        return query.executeUpdate();
    }

    private void refreshCampaignCounters(UUID campaignId) {
        campaignRepository.refreshCounters(campaignId);
    }

    private void markCampaignCompletedIfFinished(UUID campaignId) {
        int updated = campaignRepository.markCompletedIfFinished(campaignId);
        if (updated > 0) {
            log.info("Timeline regeneration campaign {} completed", campaignId);
        }
    }

    private CampaignCounts countCampaignUsers(UUID campaignId) {
        Query query = entityManager.createNativeQuery("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE status = 'PENDING') AS pending,
                       COUNT(*) FILTER (WHERE status = 'RUNNING') AS running,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                       COUNT(*) FILTER (WHERE status = 'FAILED') AS failed,
                       COUNT(*) FILTER (WHERE status = 'SKIPPED') AS skipped
                FROM timeline_regeneration_campaign_users
                WHERE campaign_id = :campaignId
                """);
        query.setParameter("campaignId", campaignId);
        Object[] row = (Object[]) query.getSingleResult();
        return new CampaignCounts(
                toInt(row[0]),
                toInt(row[1]),
                toInt(row[2]),
                toInt(row[3]),
                toInt(row[4]),
                toInt(row[5])
        );
    }

    private void finishTimelineJob(UUID jobId, UUID userId) {
        boolean jobTerminal = jobProgressService.getJobProgress(jobId)
                .map(progress -> progress.isTerminal())
                .orElse(false);

        try {
            if (!jobTerminal) {
                jobProgressService.updateProgress(jobId, "Recalculating achievement badges", 9, 99, null);
            }
            badgeRecalculationService.recalculateAllBadgesForUser(userId);
        } catch (Exception e) {
            log.error("Failed to recalculate badges for user {} after forced timeline regeneration: {}",
                    userId, e.getMessage(), e);
        }

        if (!jobTerminal) {
            jobProgressService.updateProgress(jobId, "Timeline generation completed", 9, 100, null);
            jobProgressService.completeJob(jobId);
        }
    }

    private void failOrRetry(TimelineRegenerationCampaignClaim work, UUID jobId, int maxAttempts, String errorMessage) {
        failJobIfActive(jobId, errorMessage);

        if (work.attempts() < maxAttempts) {
            campaignUserRepository.markRetry(work.campaignUserId(), errorMessage, nextAttemptAt(work.attempts()));
        } else {
            campaignUserRepository.markFailed(work.campaignUserId(), errorMessage);
        }

        refreshCampaignCounters(work.campaignId());
    }

    private void failJobIfActive(UUID jobId, String errorMessage) {
        if (jobId == null) {
            return;
        }
        boolean terminal = jobProgressService.getJobProgress(jobId)
                .map(progress -> progress.isTerminal())
                .orElse(true);
        if (!terminal) {
            jobProgressService.failJob(jobId, errorMessage == null ? "Forced timeline regeneration failed" : errorMessage);
        }
    }

    private Instant nextAttemptAt(int attempts) {
        long delayMinutes = Math.min(60, 1L << Math.min(5, Math.max(0, attempts - 1)));
        return Instant.now().plus(Duration.ofMinutes(delayMinutes));
    }

    private TimelineRegenerationCampaignSummaryDTO toSummary(TimelineRegenerationCampaignEntity campaign) {
        CampaignCounts counts = countCampaignUsers(campaign.getId());
        CampaignState state = getCampaignState(campaign.getId())
                .orElse(new CampaignState(campaign.getStatus(), campaign.getUpdatedAt(), campaign.getCompletedAt()));
        return TimelineRegenerationCampaignSummaryDTO.builder()
                .id(campaign.getId())
                .campaignKey(campaign.getCampaignKey())
                .affectedFrom(campaign.getAffectedFrom())
                .reason(campaign.getReason())
                .source(campaign.getSource())
                .status(state.status())
                .totalUsers(counts.total())
                .pendingUsers(counts.pending())
                .runningUsers(counts.running())
                .completedUsers(counts.completed())
                .failedUsers(counts.failed())
                .skippedUsers(counts.skipped())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(state.updatedAt())
                .completedAt(state.completedAt())
                .build();
    }

    private Optional<CampaignState> getCampaignState(UUID campaignId) {
        Query query = entityManager.createNativeQuery("""
                SELECT status, updated_at, completed_at
                FROM timeline_regeneration_campaigns
                WHERE id = :campaignId
                """);
        query.setParameter("campaignId", campaignId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.get(0);
        return Optional.of(new CampaignState(
                TimelineRegenerationCampaignStatus.valueOf(row[0].toString()),
                TimestampUtils.getInstantSafe(row[1]),
                row[2] == null ? null : TimestampUtils.getInstantSafe(row[2])
        ));
    }

    private TimelineRegenerationCampaignUserDTO toUserDto(TimelineRegenerationCampaignUserEntity entity) {
        UserEntity user = entity.getUser();
        return TimelineRegenerationCampaignUserDTO.builder()
                .id(entity.getId())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(entity.getStatus())
                .jobId(entity.getJobId())
                .attempts(entity.getAttempts())
                .lastError(entity.getLastError())
                .nextAttemptAt(entity.getNextAttemptAt())
                .claimedAt(entity.getClaimedAt())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private int toInt(Object value) {
        return ((Number) value).intValue();
    }

    private record CampaignCounts(
            int total,
            int pending,
            int running,
            int completed,
            int failed,
            int skipped
    ) {
    }

    private record CampaignState(
            TimelineRegenerationCampaignStatus status,
            Instant updatedAt,
            Instant completedAt
    ) {
    }
}

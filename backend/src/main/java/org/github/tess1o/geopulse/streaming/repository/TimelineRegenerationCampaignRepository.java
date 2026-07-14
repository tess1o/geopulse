package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TimelineRegenerationCampaignRepository implements PanacheRepositoryBase<TimelineRegenerationCampaignEntity, UUID> {

    public Optional<TimelineRegenerationCampaignEntity> findByCampaignKey(String campaignKey) {
        return find("campaignKey", campaignKey).firstResultOptional();
    }

    public List<TimelineRegenerationCampaignEntity> findActiveCampaigns() {
        return find("status = ?1 order by createdAt asc", TimelineRegenerationCampaignStatus.ACTIVE).list();
    }

    public List<TimelineRegenerationCampaignEntity> findAllByCreatedAtDesc() {
        return find("order by createdAt desc").list();
    }

    @Transactional
    public void refreshCounters(UUID campaignId) {
        Query query = getEntityManager().createNativeQuery("""
                UPDATE timeline_regeneration_campaigns c
                SET total_users = counts.total,
                    completed_users = counts.completed,
                    failed_users = counts.failed,
                    skipped_users = counts.skipped,
                    updated_at = NOW()
                FROM (
                    SELECT COUNT(*)::INTEGER AS total,
                           COUNT(*) FILTER (WHERE status = 'COMPLETED')::INTEGER AS completed,
                           COUNT(*) FILTER (WHERE status = 'FAILED')::INTEGER AS failed,
                           COUNT(*) FILTER (WHERE status = 'SKIPPED')::INTEGER AS skipped
                    FROM timeline_regeneration_campaign_users
                    WHERE campaign_id = :campaignId
                ) counts
                WHERE c.id = :campaignId
                """);
        query.setParameter("campaignId", campaignId);
        query.executeUpdate();
    }

    @Transactional
    public int markCompletedIfFinished(UUID campaignId) {
        Query query = getEntityManager().createNativeQuery("""
                UPDATE timeline_regeneration_campaigns c
                SET status = 'COMPLETED',
                    completed_at = NOW(),
                    updated_at = NOW()
                WHERE c.id = :campaignId
                  AND c.status = 'ACTIVE'
                  AND (
                      NOT EXISTS (
                          SELECT 1
                          FROM timeline_regeneration_campaign_users cu
                          WHERE cu.campaign_id = c.id
                      )
                      OR NOT EXISTS (
                          SELECT 1
                          FROM timeline_regeneration_campaign_users cu
                          WHERE cu.campaign_id = c.id
                            AND cu.status NOT IN ('COMPLETED', 'SKIPPED')
                      )
                  )
                """);
        query.setParameter("campaignId", campaignId);
        return query.executeUpdate();
    }

    @Transactional
    public void reactivate(UUID campaignId) {
        update("status = ?1, completedAt = null, updatedAt = ?2 where id = ?3",
                TimelineRegenerationCampaignStatus.ACTIVE,
                java.time.Instant.now(),
                campaignId);
    }
}

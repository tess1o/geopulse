package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineCorrectionEffectEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TimelineCorrectionEffectRepository implements PanacheRepositoryBase<TimelineCorrectionEffectEntity, Long> {
    public void deleteForCorrection(Long correctionId) {
        var effects = find("correction.id", correctionId).list();
        for (var effect : effects) {
            effect.getCorrection().getEffects().remove(effect);
            delete(effect);
        }
    }

    public List<TimelineCorrectionEffectEntity> findForTargets(String targetType, Collection<Long> targetIds) {
        if (targetIds.isEmpty()) return List.of();
        return find("targetType = ?1 and targetId in ?2", targetType, targetIds).list();
    }

    public java.util.Map<Long, Integer> countForTargets(String targetType, Collection<Long> targetIds) {
        if (targetIds.isEmpty()) return java.util.Map.of();
        var rows = getEntityManager().createQuery("""
                select targetId, count(*) from TimelineCorrectionEffectEntity
                where targetType = :targetType and targetId in :targetIds group by targetId
                """, Object[].class)
                .setParameter("targetType", targetType).setParameter("targetIds", targetIds).getResultList();
        java.util.Map<Long, Integer> counts = new java.util.HashMap<>();
        for (var row : rows) counts.put((Long) row[0], Math.toIntExact((Long) row[1]));
        return counts;
    }

    public void deleteMissingTargets(UUID userId) {
        getEntityManager().createQuery("""
                delete from TimelineCorrectionEffectEntity effect
                where effect.correction.user.id = :userId
                  and ((effect.targetType = 'STAY' and not exists (
                           select stay.id from TimelineStayEntity stay
                           where stay.id = effect.targetId and stay.user.id = :userId))
                    or (effect.targetType = 'TRIP' and not exists (
                           select trip.id from TimelineTripEntity trip
                           where trip.id = effect.targetId and trip.user.id = :userId))
                    or (effect.targetType = 'DATA_GAP' and not exists (
                           select gap.id from TimelineDataGapEntity gap
                           where gap.id = effect.targetId and gap.user.id = :userId)))
                """).setParameter("userId", userId).executeUpdate();
    }
}

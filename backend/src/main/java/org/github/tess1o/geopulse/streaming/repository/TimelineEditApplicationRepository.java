package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditApplicationEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TimelineEditApplicationRepository implements PanacheRepositoryBase<TimelineEditApplicationEntity, Long> {
    @Override
    public boolean deleteById(Long id) {
        TimelineEditApplicationEntity application = findById(id);
        if (application == null) return false;
        // Keep the managed inverse side consistent when superseding or reverting a loaded edit.
        application.getEdit().setApplication(null);
        delete(application);
        return true;
    }

    public List<TimelineEditApplicationEntity> findForTargets(String targetType, Collection<Long> targetIds) {
        if (targetIds.isEmpty()) return List.of();
        return find("targetType = ?1 and targetId in ?2", targetType, targetIds).list();
    }
    public java.util.Map<Long, Integer> countForTargets(String targetType, Collection<Long> targetIds) {
        if (targetIds.isEmpty()) return java.util.Map.of();
        var rows = getEntityManager().createQuery("""
                select targetId, count(*) from TimelineEditApplicationEntity
                where targetType = :targetType and targetId in :targetIds group by targetId
                """, Object[].class)
                .setParameter("targetType", targetType).setParameter("targetIds", targetIds).getResultList();
        java.util.Map<Long, Integer> counts = new java.util.HashMap<>();
        for (var row : rows) counts.put((Long) row[0], Math.toIntExact((Long) row[1]));
        return counts;
    }

    public void deleteMissingTargets(UUID userId) {
        getEntityManager().createQuery("""
                delete from TimelineEditApplicationEntity application
                where application.edit.user.id = :userId
                  and ((application.targetType = 'STAY' and not exists (
                           select stay.id from TimelineStayEntity stay
                           where stay.id = application.targetId and stay.user.id = :userId))
                    or (application.targetType = 'TRIP' and not exists (
                           select trip.id from TimelineTripEntity trip
                           where trip.id = application.targetId and trip.user.id = :userId))
                    or (application.targetType = 'DATA_GAP' and not exists (
                           select gap.id from TimelineDataGapEntity gap
                           where gap.id = application.targetId and gap.user.id = :userId)))
                """).setParameter("userId", userId).executeUpdate();
    }

}

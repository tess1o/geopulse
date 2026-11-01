package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditApplicationEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TimelineEditApplicationRepository implements PanacheRepositoryBase<TimelineEditApplicationEntity, Long> {
    public List<TimelineEditApplicationEntity> findForTargets(String targetType, Collection<Long> targetIds) {
        if (targetIds.isEmpty()) return List.of();
        return find("targetType = ?1 and targetId in ?2", targetType, targetIds).list();
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

package org.github.tess1o.geopulse.notes.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TimelineNoteRepository implements PanacheRepository<TimelineNoteEntity> {

    public List<TimelineNoteEntity> findByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return find("""
                user.id = ?1
                    AND deletedAt IS NULL
                    AND eventTime >= ?2
                    AND eventTime <= ?3
                ORDER BY eventTime ASC, id ASC
                """, userId, startTime, endTime).list();
    }

    public Optional<TimelineNoteEntity> findActiveByIdAndUserId(Long noteId, UUID userId) {
        return find("id = ?1 AND user.id = ?2 AND deletedAt IS NULL", noteId, userId)
                .firstResultOptional();
    }

    public List<TimelineNoteEntity> findUnmatchedAnchoredNotes(UUID userId, NoteAnchorType anchorType) {
        String anchorClause = anchorType == NoteAnchorType.STAY ? "stay IS NULL" : "trip IS NULL";
        return find("user.id = ?1 AND deletedAt IS NULL AND anchorType = ?2 AND " + anchorClause + " ORDER BY sourceItemStartTime ASC",
                userId, anchorType).list();
    }
}

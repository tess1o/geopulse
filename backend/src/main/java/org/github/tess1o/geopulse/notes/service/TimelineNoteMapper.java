package org.github.tess1o.geopulse.notes.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.notes.model.MemosLocation;
import org.github.tess1o.geopulse.notes.model.MemosMemo;
import org.github.tess1o.geopulse.notes.model.MemosPreferences;
import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.NoteDto;
import org.github.tess1o.geopulse.notes.model.NoteLocationSource;
import org.github.tess1o.geopulse.notes.model.NoteSource;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.locationtech.jts.geom.Point;

@ApplicationScoped
public class TimelineNoteMapper {

    @Inject
    NoteContentFormatter contentFormatter;

    @Inject
    MemosPreferencesService memosPreferencesService;

    NoteDto mapLocalNote(TimelineNoteEntity note) {
        Long anchorId = null;
        if (note.getAnchorType() == NoteAnchorType.STAY && note.getStay() != null) {
            anchorId = note.getStay().getId();
        } else if (note.getAnchorType() == NoteAnchorType.TRIP && note.getTrip() != null) {
            anchorId = note.getTrip().getId();
        }

        Point location = note.getLocation();
        return NoteDto.builder()
                .id(note.getId())
                .source(NoteSource.GEOPULSE)
                .title(note.getTitle())
                .contentMarkdown(note.getContentMarkdown())
                .snippet(note.getSnippet())
                .eventTime(note.getEventTime())
                .latitude(location != null ? location.getY() : null)
                .longitude(location != null ? location.getX() : null)
                .locationSource(note.getLocationSource())
                .anchorType(note.getAnchorType())
                .anchorId(anchorId)
                .editable(true)
                .truncated(false)
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }

    NoteDto mapMemosMemo(MemosMemo memo, MemosPreferences preferences) {
        if (memo == null || memo.getCreateTime() == null) {
            return null;
        }

        String content = memo.getContent() != null ? memo.getContent() : "";
        boolean truncated = contentFormatter.isTooLarge(content, preferences.getMaxContentBytes());
        String renderedContent = truncated ? contentFormatter.truncateByChars(content, preferences.getMaxContentBytes()) : content;
        MemosLocation location = memo.getLocation();

        return NoteDto.builder()
                .source(NoteSource.MEMOS)
                .externalId(resolveMemosExternalId(memo))
                .externalUrl(buildMemosExternalUrl(preferences.getServerUrl(), memo))
                .title(null)
                .contentMarkdown(renderedContent)
                .snippet(contentFormatter.resolveSnippet(memo.getSnippet(), content))
                .eventTime(memo.getCreateTime())
                .latitude(location != null ? location.getLatitude() : null)
                .longitude(location != null ? location.getLongitude() : null)
                .locationSource(location != null && location.getLatitude() != null && location.getLongitude() != null
                        ? NoteLocationSource.EXPLICIT
                        : NoteLocationSource.NONE)
                .anchorType(NoteAnchorType.TIMESTAMP)
                .editable(false)
                .truncated(truncated)
                .createdAt(memo.getCreateTime())
                .updatedAt(memo.getUpdateTime())
                .build();
    }

    private String resolveMemosExternalId(MemosMemo memo) {
        if (memo.getName() != null && !memo.getName().isBlank()) {
            return memo.getName();
        }
        return memo.getUid();
    }

    private String buildMemosExternalUrl(String serverUrl, MemosMemo memo) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return null;
        }
        String normalized = memosPreferencesService.normalizeServerUrl(serverUrl);
        if (memo.getUid() != null && !memo.getUid().isBlank()) {
            return normalized + "/m/" + memo.getUid();
        }
        String externalId = resolveMemosExternalId(memo);
        return externalId == null ? normalized : normalized + "/" + externalId;
    }
}

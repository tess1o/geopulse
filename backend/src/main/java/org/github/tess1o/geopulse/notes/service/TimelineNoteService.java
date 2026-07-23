package org.github.tess1o.geopulse.notes.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.notes.model.CreateNoteRequest;
import org.github.tess1o.geopulse.notes.model.MemosConfigResponse;
import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.NoteDestination;
import org.github.tess1o.geopulse.notes.model.NoteDto;
import org.github.tess1o.geopulse.notes.model.NoteLocationSource;
import org.github.tess1o.geopulse.notes.model.NoteMapMarkersResponse;
import org.github.tess1o.geopulse.notes.model.NoteSearchResponse;
import org.github.tess1o.geopulse.notes.model.NoteSource;
import org.github.tess1o.geopulse.notes.model.TestMemosConnectionRequest;
import org.github.tess1o.geopulse.notes.model.TestMemosConnectionResponse;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.github.tess1o.geopulse.notes.model.UpdateMemosConfigRequest;
import org.github.tess1o.geopulse.notes.model.UpdateNoteRequest;
import org.github.tess1o.geopulse.notes.repository.TimelineNoteRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@ApplicationScoped
public class TimelineNoteService {

    @Inject
    TimelineNoteRepository noteRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    NoteContentFormatter contentFormatter;

    @Inject
    TimelineNoteMapper noteMapper;

    @Inject
    TimelineNoteSearchSupport searchSupport;

    @Inject
    TimelineNoteLocationService locationService;

    @Inject
    TimelineNoteAnchorReattachmentService reattachmentService;

    @Inject
    MemosNoteService memosNoteService;

    @Inject
    TimelineNoteMapMarkerService mapMarkerService;

    public CompletableFuture<NoteSearchResponse> searchNotes(
            UUID userId,
            Instant startTime,
            Instant endTime,
            boolean includeExternal,
            Integer limit,
            Double latitude,
            Double longitude,
            Double radiusMeters
    ) {
        int resolvedLimit = searchSupport.resolveLimit(limit);
        List<NoteDto> localNotes = noteRepository.findByUserIdAndTimeRange(userId, startTime, endTime).stream()
                .map(noteMapper::mapLocalNote)
                .collect(Collectors.toCollection(ArrayList::new));

        List<NoteDto> externalNotes = includeExternal
                ? memosNoteService.loadNotes(userId, startTime, endTime, resolvedLimit)
                : List.of();

        List<NoteDto> combined = new ArrayList<>(localNotes);
        combined.addAll(externalNotes);
        locationService.resolveTimelineLocations(userId, startTime, endTime, combined);
        List<NoteDto> filtered = searchSupport.filterByRadius(combined, latitude, longitude, radiusMeters).stream()
                .sorted(searchSupport.noteComparator())
                .limit(resolvedLimit)
                .toList();

        return CompletableFuture.completedFuture(NoteSearchResponse.builder()
                .notes(filtered)
                .totalCount(filtered.size())
                .geopulseCount((int) filtered.stream().filter(note -> note.getSource() == NoteSource.GEOPULSE).count())
                .memosCount((int) filtered.stream().filter(note -> note.getSource() == NoteSource.MEMOS).count())
                .build());
    }

    public CompletableFuture<NoteMapMarkersResponse> getMapMarkers(
            UUID userId,
            Instant startTime,
            Instant endTime,
            boolean includeExternal,
            Integer coordinatePrecision
    ) {
        return searchNotes(
                userId,
                startTime,
                endTime,
                includeExternal,
                TimelineNoteConstants.MAX_SEARCH_LIMIT,
                null,
                null,
                null
        ).thenApply(response -> mapMarkerService.buildMarkers(response.getNotes(), coordinatePrecision));
    }

    @Transactional
    public CompletableFuture<NoteDto> createNote(UUID userId, CreateNoteRequest request) {
        NoteDestination destination = memosNoteService.resolveDestination(userId, request.getDestination());
        if (destination == NoteDestination.MEMOS) {
            return memosNoteService.createNote(userId, request);
        }
        return CompletableFuture.completedFuture(createLocalNote(userId, request));
    }

    @Transactional
    public NoteDto updateLocalNote(UUID userId, Long noteId, UpdateNoteRequest request) {
        TimelineNoteEntity note = noteRepository.findActiveByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new NoSuchElementException("Note not found"));

        note.setTitle(contentFormatter.normalizeTitle(request.getTitle()));
        note.setContentMarkdown(request.getContentMarkdown().trim());
        note.setSnippet(contentFormatter.buildSnippet(request.getContentMarkdown()));
        if (request.getEventTime() != null) {
            note.setEventTime(request.getEventTime());
        }
        if (request.getLatitude() != null && request.getLongitude() != null) {
            note.setLocation(GeoUtils.createPoint(request.getLongitude(), request.getLatitude()));
            note.setLocationSource(NoteLocationSource.EXPLICIT);
        }
        return noteMapper.mapLocalNote(note);
    }

    @Transactional
    public void deleteLocalNote(UUID userId, Long noteId) {
        TimelineNoteEntity note = noteRepository.findActiveByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new NoSuchElementException("Note not found"));
        note.setDeletedAt(Instant.now());
    }

    public Optional<MemosConfigResponse> getMemosConfig(UUID userId) {
        return memosNoteService.getConfig(userId);
    }

    @Transactional
    public void updateMemosConfig(UUID userId, UpdateMemosConfigRequest request) {
        memosNoteService.updateConfig(userId, request);
    }

    public CompletableFuture<TestMemosConnectionResponse> testMemosConnection(UUID userId, TestMemosConnectionRequest request) {
        return memosNoteService.testConnection(userId, request);
    }

    @Transactional
    public int reattachAnchoredNotes(UUID userId) {
        return reattachmentService.reattachAnchoredNotes(userId);
    }

    protected NoteDto createLocalNote(UUID userId, CreateNoteRequest request) {
        UserEntity userRef = entityManager.getReference(UserEntity.class, userId);
        TimelineNoteEntity note = new TimelineNoteEntity();
        note.setUser(userRef);
        note.setTitle(contentFormatter.normalizeTitle(request.getTitle()));
        note.setContentMarkdown(request.getContentMarkdown().trim());
        note.setSnippet(contentFormatter.buildSnippet(request.getContentMarkdown()));
        note.setAnchorType(request.getAnchorType() != null ? request.getAnchorType() : NoteAnchorType.TIMESTAMP);
        note.setEventTime(locationService.resolveEventTime(userId, request));

        if (request.getLatitude() != null && request.getLongitude() != null) {
            note.setLocation(GeoUtils.createPoint(request.getLongitude(), request.getLatitude()));
            note.setLocationSource(NoteLocationSource.EXPLICIT);
        } else {
            note.setLocationSource(NoteLocationSource.NONE);
        }

        locationService.attachAnchor(userId, note, request.getAnchorId());
        noteRepository.persist(note);
        return noteMapper.mapLocalNote(note);
    }
}

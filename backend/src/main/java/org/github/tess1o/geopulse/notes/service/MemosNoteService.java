package org.github.tess1o.geopulse.notes.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.notes.client.MemosClient;
import org.github.tess1o.geopulse.notes.model.CreateNoteRequest;
import org.github.tess1o.geopulse.notes.model.MemosConfigResponse;
import org.github.tess1o.geopulse.notes.model.MemosCreateMemoRequest;
import org.github.tess1o.geopulse.notes.model.MemosListResponse;
import org.github.tess1o.geopulse.notes.model.MemosLocation;
import org.github.tess1o.geopulse.notes.model.MemosMemo;
import org.github.tess1o.geopulse.notes.model.MemosPreferences;
import org.github.tess1o.geopulse.notes.model.NoteDestination;
import org.github.tess1o.geopulse.notes.model.NoteDto;
import org.github.tess1o.geopulse.notes.model.TestMemosConnectionRequest;
import org.github.tess1o.geopulse.notes.model.TestMemosConnectionResponse;
import org.github.tess1o.geopulse.notes.model.UpdateMemosConfigRequest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
@Slf4j
public class MemosNoteService {

    @Inject
    UserRepository userRepository;

    @Inject
    MemosClient memosClient;

    @Inject
    TimelineNoteMapper noteMapper;

    @Inject
    TimelineNoteSearchSupport searchSupport;

    @Inject
    MemosPreferencesService preferencesService;

    @Inject
    MemosSearchCache searchCache;

    @Inject
    TimelineNoteLocationService locationService;

    List<NoteDto> loadNotes(UUID userId, Instant startTime, Instant endTime, int limit) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        MemosPreferences preferences = preferencesService.applyDefaults(user.getMemosPreferences());
        if (!Boolean.TRUE.equals(preferences.getEnabled())) {
            return List.of();
        }
        if (preferences.getServerUrl() == null || preferences.getServerUrl().isBlank()
                || preferences.getApiKey() == null || preferences.getApiKey().isBlank()) {
            return List.of();
        }

        int providerLimit = Math.min(limit, Math.max(1, preferences.getMaxNotesPerRequest()));
        List<String> includeTags = preferences.getIncludeTags();
        List<String> excludeTags = preferences.getExcludeTags();
        Set<String> excludedTagSet = excludeTags == null || excludeTags.isEmpty() ? Set.of() : Set.copyOf(excludeTags);
        boolean searchCacheEnabled = Boolean.TRUE.equals(preferences.getSearchCacheEnabled());
        if (searchCacheEnabled) {
            List<NoteDto> cached = searchCache.get(userId, startTime, endTime, providerLimit, includeTags, excludeTags);
            if (cached != null) {
                return cached;
            }
        }

        try {
            List<NoteDto> notes = memosClient.listMemosAllPages(
                            preferences.getServerUrl(),
                            preferences.getApiKey(),
                            startTime,
                            endTime,
                            providerLimit,
                            includeTags
                    )
                    .stream()
                    .filter(memo -> !hasExcludedTag(memo, excludedTagSet))
                    .map(memo -> noteMapper.mapMemosMemo(memo, preferences))
                    .filter(Objects::nonNull)
                    .sorted(searchSupport.noteComparator())
                    .toList();
            if (searchCacheEnabled) {
                searchCache.put(userId, startTime, endTime, providerLimit, includeTags, excludeTags, notes);
            }
            return notes;
        } catch (Exception e) {
            log.error("Failed to load Memos notes for user {}: {}", userId, e.getMessage(), e);
            throw e instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new RuntimeException(e);
        }
    }

    CompletableFuture<NoteDto> createNote(UUID userId, CreateNoteRequest request) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        MemosPreferences preferences = preferencesService.applyDefaults(user.getMemosPreferences());
        if (!Boolean.TRUE.equals(preferences.getEnabled())
                || preferences.getServerUrl() == null || preferences.getServerUrl().isBlank()
                || preferences.getApiKey() == null || preferences.getApiKey().isBlank()) {
            throw new IllegalStateException("Memos is not configured");
        }

        Instant eventTime = locationService.resolveEventTime(userId, request);
        TimelineNoteResolvedLocation location = locationService.resolveCreateRequestLocation(userId, request, eventTime);
        MemosCreateMemoRequest createRequest = MemosCreateMemoRequest.builder()
                .content(request.getContentMarkdown().trim())
                .visibility(request.getVisibility() != null ? request.getVisibility() : preferences.getDefaultVisibility())
                .createTime(eventTime)
                .location(location.latitude() != null && location.longitude() != null
                        ? MemosLocation.builder()
                        .latitude(location.latitude())
                        .longitude(location.longitude())
                        .placeholder(location.placeholder())
                        .build()
                        : null)
                .build();

        MemosMemo memo = memosClient.createMemo(preferences.getServerUrl(), preferences.getApiKey(), createRequest);
        searchCache.invalidateForUser(userId);
        NoteDto dto = noteMapper.mapMemosMemo(memo, preferences);
        if (dto.getLatitude() == null && location.latitude() != null) {
            dto.setLatitude(location.latitude());
            dto.setLongitude(location.longitude());
            dto.setLocationSource(location.source());
        }
        return CompletableFuture.completedFuture(dto);
    }

    NoteDestination resolveDestination(UUID userId, NoteDestination requestedDestination) {
        if (requestedDestination != null) {
            return requestedDestination;
        }
        UserEntity user = userRepository.findById(userId);
        MemosPreferences preferences = user != null ? preferencesService.applyDefaults(user.getMemosPreferences()) : new MemosPreferences();
        if (Boolean.TRUE.equals(preferences.getEnabled()) && preferences.getDefaultSaveDestination() != null) {
            return preferences.getDefaultSaveDestination();
        }
        return NoteDestination.GEOPULSE;
    }

    Optional<MemosConfigResponse> getConfig(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        if (user == null || user.getMemosPreferences() == null) {
            return Optional.empty();
        }
        return Optional.of(preferencesService.toConfigResponse(preferencesService.applyDefaults(user.getMemosPreferences())));
    }

    void updateConfig(UUID userId, UpdateMemosConfigRequest request) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        MemosPreferences existing = preferencesService.applyDefaults(user.getMemosPreferences());
        String apiKey = request.getApiKey();
        if ((apiKey == null || apiKey.isBlank()) && existing.getApiKey() != null && !existing.getApiKey().isBlank()) {
            apiKey = existing.getApiKey();
        }

        String serverUrl = preferencesService.normalizeServerUrl(request.getServerUrl());
        boolean enabled = Boolean.TRUE.equals(request.getEnabled());
        if (enabled && (serverUrl == null || serverUrl.isBlank())) {
            throw new IllegalArgumentException("Server URL is required when Memos integration is enabled");
        }
        if (enabled && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalArgumentException("API key is required when Memos integration is enabled");
        }

        MemosPreferences preferences = MemosPreferences.builder()
                .serverUrl(serverUrl)
                .apiKey(apiKey)
                .enabled(enabled)
                .defaultSaveDestination(request.getDefaultSaveDestination() != null
                        ? request.getDefaultSaveDestination()
                        : existing.getDefaultSaveDestination())
                .defaultVisibility(request.getDefaultVisibility() != null
                        ? request.getDefaultVisibility()
                        : existing.getDefaultVisibility())
                .maxNotesPerRequest(preferencesService.resolvePositiveOrDefault(
                        request.getMaxNotesPerRequest(),
                        existing.getMaxNotesPerRequest(),
                        TimelineNoteConstants.DEFAULT_SEARCH_LIMIT
                ))
                .maxContentBytes(preferencesService.resolvePositiveOrDefault(
                        request.getMaxContentBytes(),
                        existing.getMaxContentBytes(),
                        TimelineNoteConstants.DEFAULT_MEMOS_CONTENT_BYTES
                ))
                .searchCacheEnabled(request.getSearchCacheEnabled() != null
                        ? request.getSearchCacheEnabled()
                        : existing.getSearchCacheEnabled())
                .includeTags(preferencesService.normalizeTagList(request.getIncludeTags() != null
                        ? request.getIncludeTags()
                        : existing.getIncludeTags()))
                .excludeTags(preferencesService.normalizeTagList(request.getExcludeTags() != null
                        ? request.getExcludeTags()
                        : existing.getExcludeTags()))
                .build();

        user.setMemosPreferences(preferences);
        userRepository.persist(user);
        searchCache.invalidateForUser(userId);
    }

    private boolean hasExcludedTag(MemosMemo memo, Set<String> excludeTags) {
        if (memo == null || excludeTags == null || excludeTags.isEmpty()) {
            return false;
        }
        return preferencesService.normalizeTagList(memo.getTags()).stream().anyMatch(excludeTags::contains);
    }

    CompletableFuture<TestMemosConnectionResponse> testConnection(UUID userId, TestMemosConnectionRequest request) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            return CompletableFuture.completedFuture(TestMemosConnectionResponse.builder()
                    .success(false)
                    .message("User not found")
                    .build());
        }

        String serverUrl = preferencesService.normalizeServerUrl(request.getServerUrl());
        String apiKey = request.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            MemosPreferences preferences = user.getMemosPreferences();
            if (preferences != null && preferences.getApiKey() != null && !preferences.getApiKey().isBlank()) {
                apiKey = preferences.getApiKey();
            } else {
                return CompletableFuture.completedFuture(TestMemosConnectionResponse.builder()
                        .success(false)
                        .message("API key is required")
                        .details("No API key provided and no saved API key found")
                        .build());
            }
        }

        try {
            MemosListResponse response = memosClient.fetchMemosPage(serverUrl, apiKey, 1, null, null);
            return CompletableFuture.completedFuture(TestMemosConnectionResponse.builder()
                    .success(true)
                    .message("Successfully connected to Memos server")
                    .details("Server returned " + (response.getMemos() == null ? 0 : response.getMemos().size()) + " memo(s)")
                    .build());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(TestMemosConnectionResponse.builder()
                    .success(false)
                    .message(preferencesService.resolveErrorMessage(e))
                    .details(e.getMessage())
                    .build());
        }
    }
}

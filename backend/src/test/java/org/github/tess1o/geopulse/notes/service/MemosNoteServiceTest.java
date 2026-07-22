package org.github.tess1o.geopulse.notes.service;

import org.github.tess1o.geopulse.notes.client.MemosClient;
import org.github.tess1o.geopulse.notes.model.MemosMemo;
import org.github.tess1o.geopulse.notes.model.MemosPreferences;
import org.github.tess1o.geopulse.notes.model.NoteDestination;
import org.github.tess1o.geopulse.notes.model.NoteDto;
import org.github.tess1o.geopulse.notes.model.NoteSource;
import org.github.tess1o.geopulse.notes.model.UpdateMemosConfigRequest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class MemosNoteServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant START_TIME = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END_TIME = Instant.parse("2026-07-02T00:00:00Z");

    @Mock
    UserRepository userRepository;

    @Mock
    MemosClient memosClient;

    @Mock
    TimelineNoteMapper noteMapper;

    @Mock
    MemosSearchCache searchCache;

    MemosNoteService service;

    @BeforeEach
    void setUp() {
        service = new MemosNoteService();
        service.userRepository = userRepository;
        service.memosClient = memosClient;
        service.noteMapper = noteMapper;
        service.searchSupport = new TimelineNoteSearchSupport();
        service.preferencesService = new MemosPreferencesService();
        service.searchCache = searchCache;
    }

    @Test
    void loadNotesBypassesSearchCacheWhenDisabled() {
        UserEntity user = userWithPreferences(false);
        MemosMemo memo = new MemosMemo();
        NoteDto dto = NoteDto.builder()
                .source(NoteSource.MEMOS)
                .eventTime(START_TIME)
                .build();

        when(userRepository.findById(USER_ID)).thenReturn(user);
        when(memosClient.listMemosAllPages("https://memos.example.com", "secret", START_TIME, END_TIME, 1000))
                .thenReturn(List.of(memo));
        when(noteMapper.mapMemosMemo(eq(memo), any(MemosPreferences.class))).thenReturn(dto);

        List<NoteDto> notes = service.loadNotes(USER_ID, START_TIME, END_TIME, 1000);

        assertSame(dto, notes.getFirst());
        verify(searchCache, never()).get(any(), any(), any(), anyInt());
        verify(searchCache, never()).put(any(), any(), any(), anyInt(), any());
    }

    @Test
    void loadNotesReturnsCachedResultWhenCacheEnabled() {
        UserEntity user = userWithPreferences(true);
        NoteDto cached = NoteDto.builder()
                .source(NoteSource.MEMOS)
                .eventTime(START_TIME)
                .build();

        when(userRepository.findById(USER_ID)).thenReturn(user);
        when(searchCache.get(USER_ID, START_TIME, END_TIME, 1000)).thenReturn(List.of(cached));

        List<NoteDto> notes = service.loadNotes(USER_ID, START_TIME, END_TIME, 1000);

        assertSame(cached, notes.getFirst());
        verify(memosClient, never()).listMemosAllPages(any(), any(), any(), any(), anyInt());
        verify(searchCache, never()).put(any(), any(), any(), anyInt(), any());
    }

    @Test
    void updateConfigPersistsDisabledSearchCacheAndInvalidatesUserEntries() {
        UserEntity user = userWithPreferences(true);
        UpdateMemosConfigRequest request = new UpdateMemosConfigRequest();
        request.setServerUrl("https://memos.example.com/");
        request.setEnabled(true);
        request.setSearchCacheEnabled(false);

        when(userRepository.findById(USER_ID)).thenReturn(user);

        service.updateConfig(USER_ID, request);

        assertFalse(user.getMemosPreferences().getSearchCacheEnabled());
        verify(userRepository).persist(user);
        verify(searchCache).invalidateForUser(USER_ID);
    }

    private UserEntity userWithPreferences(boolean searchCacheEnabled) {
        UserEntity user = new UserEntity();
        user.setMemosPreferences(MemosPreferences.builder()
                .serverUrl("https://memos.example.com")
                .apiKey("secret")
                .enabled(true)
                .defaultSaveDestination(NoteDestination.GEOPULSE)
                .searchCacheEnabled(searchCacheEnabled)
                .build());
        return user;
    }
}

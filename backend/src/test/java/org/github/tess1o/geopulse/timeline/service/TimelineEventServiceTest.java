package org.github.tess1o.geopulse.timeline.service;

import org.github.tess1o.geopulse.timeline.events.FavoriteRenamedEvent;
import org.github.tess1o.geopulse.timeline.model.ImpactAnalysis;
import org.github.tess1o.geopulse.timeline.model.ImpactAnalysis.ImpactType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the new simplified TimelineEventService.
 * Tests the hybrid fast/slow path approach for favorite changes.
 */
@ExtendWith(MockitoExtension.class)
class TimelineEventServiceTest {

    @Mock private FavoriteImpactAnalyzer impactAnalyzer;
    @Mock private TimelineCacheService timelineCacheService;
    @Mock private TimelineBackgroundService backgroundService;
    @Mock private jakarta.persistence.EntityManager entityManager;
    @Mock private jakarta.persistence.Query query;

    private TimelineEventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new TimelineEventService();
        
        // Inject mocks using reflection
        try {
            var impactField = TimelineEventService.class.getDeclaredField("impactAnalyzer");
            impactField.setAccessible(true);
            impactField.set(eventService, impactAnalyzer);
            
            var cacheField = TimelineEventService.class.getDeclaredField("timelineCacheService");
            cacheField.setAccessible(true);
            cacheField.set(eventService, timelineCacheService);
            
            var backgroundField = TimelineEventService.class.getDeclaredField("backgroundService");
            backgroundField.setAccessible(true);
            backgroundField.set(eventService, backgroundService);
            
            var entityManagerField = TimelineEventService.class.getDeclaredField("entityManager");
            entityManagerField.setAccessible(true);
            entityManagerField.set(eventService, entityManager);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }
        
        // Setup EntityManager mocks (lenient because only used for name-only changes)
        lenient().when(entityManager.createQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.executeUpdate()).thenReturn(1);
    }

    @Test
    void testFavoriteRenamed_NameOnlyChange_ShouldUseFastPath() {
        // Arrange
        Long favoriteId = 123L;
        UUID userId = UUID.randomUUID();
        FavoriteRenamedEvent event = FavoriteRenamedEvent.builder()
                .favoriteId(favoriteId)
                .userId(userId)
                .oldName("Old Name")
                .newName("New Name")
                .build();
        
        ImpactAnalysis nameOnlyImpact = ImpactAnalysis.nameOnly(favoriteId, "Simple rename");
        when(impactAnalyzer.analyzeRename(event)).thenReturn(nameOnlyImpact);

        // Act
        eventService.onFavoriteRenamed(event);

        // Assert
        verify(impactAnalyzer).analyzeRename(event);
        // Note: Direct cache update is handled internally, we can't easily verify private method calls
        verify(backgroundService, never()).queueHighPriorityRegeneration(any(), any());
    }

    @Test
    void testFavoriteRenamed_StructuralChange_ShouldUseSlowPath() {
        // Arrange
        Long favoriteId = 456L;
        UUID userId = UUID.randomUUID();
        FavoriteRenamedEvent event = FavoriteRenamedEvent.builder()
                .favoriteId(favoriteId)
                .userId(userId)
                .oldName("Old Name")
                .newName("New Name")
                .build();
        
        List<LocalDate> affectedDates = List.of(LocalDate.now().minusDays(1), LocalDate.now().minusDays(2));
        ImpactAnalysis structuralImpact = ImpactAnalysis.structural(favoriteId, affectedDates, "Complex change");
        when(impactAnalyzer.analyzeRename(event)).thenReturn(structuralImpact);

        // Act
        eventService.onFavoriteRenamed(event);

        // Assert
        verify(impactAnalyzer).analyzeRename(event);
        // Note: Cache deletion and background queueing are handled internally
        verify(backgroundService).queueHighPriorityRegeneration(eq(userId), eq(affectedDates));
    }
}
package org.github.tess1o.geopulse.timeline.service;

import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.timeline.events.FavoriteRenamedEvent;
import org.github.tess1o.geopulse.timeline.model.ImpactAnalysis;
import org.github.tess1o.geopulse.timeline.model.ImpactAnalysis.ImpactType;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FavoriteImpactAnalyzer.
 * Tests the logic for determining impact types of favorite changes.
 */
@ExtendWith(MockitoExtension.class)
class FavoriteImpactAnalyzerTest {

    @Mock private FavoritesRepository favoritesRepository;
    @Mock private TimelineStayRepository timelineStayRepository;

    private FavoriteImpactAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new FavoriteImpactAnalyzer();
        
        // Inject mocks using reflection
        try {
            var favoritesField = FavoriteImpactAnalyzer.class.getDeclaredField("favoritesRepository");
            favoritesField.setAccessible(true);
            favoritesField.set(analyzer, favoritesRepository);
            
            var staysField = FavoriteImpactAnalyzer.class.getDeclaredField("timelineStayRepository");
            staysField.setAccessible(true);
            staysField.set(analyzer, timelineStayRepository);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }
    }

    @Test
    void testAnalyzeRename_SimpleFavoriteWithNoMergeImpact_ShouldReturnNameOnly() {
        // Arrange
        Long favoriteId = 123L;
        UUID userId = UUID.randomUUID();
        FavoriteRenamedEvent event = FavoriteRenamedEvent.builder()
                .favoriteId(favoriteId)
                .userId(userId)
                .oldName("Old Name")
                .newName("New Name")
                .build();
        
        FavoritesEntity favorite = new FavoritesEntity();
        favorite.setId(favoriteId);
        favorite.setName("Old Name");
        favorite.setType(FavoriteLocationType.POINT);
        favorite.setMergeImpact(false); // No merge impact
        
        when(favoritesRepository.findById(favoriteId)).thenReturn(favorite);

        // Act
        ImpactAnalysis result = analyzer.analyzeRename(event);

        // Assert
        assertNotNull(result);
        assertEquals(ImpactType.NAME_ONLY, result.getType());
        assertEquals(favoriteId, result.getFavoriteId());
        verify(timelineStayRepository, never()).findByFavoriteId(any());
    }

    @Test
    void testAnalyzeRename_ComplexFavoriteWithMergeImpact_ShouldReturnStructural() {
        // Arrange
        Long favoriteId = 456L;
        UUID userId = UUID.randomUUID();
        FavoriteRenamedEvent event = FavoriteRenamedEvent.builder()
                .favoriteId(favoriteId)
                .userId(userId)
                .oldName("Old Name")
                .newName("New Name")
                .build();
        
        FavoritesEntity favorite = new FavoritesEntity();
        favorite.setId(favoriteId);
        favorite.setName("Old Name");
        favorite.setType(FavoriteLocationType.POINT);
        favorite.setMergeImpact(true); // Has merge impact
        
        // Mock timeline stays that reference this favorite
        TimelineStayEntity stay1 = new TimelineStayEntity();
        stay1.setTimestamp(java.time.Instant.now().minusSeconds(86400)); // Yesterday
        TimelineStayEntity stay2 = new TimelineStayEntity();
        stay2.setTimestamp(java.time.Instant.now().minusSeconds(172800)); // Day before yesterday
        
        when(favoritesRepository.findById(favoriteId)).thenReturn(favorite);
        when(timelineStayRepository.findByFavoriteId(favoriteId)).thenReturn(List.of(stay1, stay2));

        // Act
        ImpactAnalysis result = analyzer.analyzeRename(event);

        // Assert
        assertNotNull(result);
        assertEquals(ImpactType.STRUCTURAL, result.getType());
        assertEquals(favoriteId, result.getFavoriteId());
        assertEquals(2, result.getAffectedDates().size());
        verify(timelineStayRepository).findByFavoriteId(favoriteId);
    }

    @Test
    void testComputeMergeImpact_AreaFavorite_ShouldReturnTrue() {
        // Arrange
        FavoritesEntity areaFavorite = new FavoritesEntity();
        areaFavorite.setType(FavoriteLocationType.AREA);
        areaFavorite.setName("Test Area");

        // Act
        boolean result = analyzer.computeMergeImpact(areaFavorite);

        // Assert
        assertTrue(result);
    }

    @Test
    void testComputeMergeImpact_PointFavorite_ShouldReturnFalse() {
        // Arrange
        FavoritesEntity pointFavorite = new FavoritesEntity();
        pointFavorite.setType(FavoriteLocationType.POINT);
        pointFavorite.setName("Test Point");

        // Act
        boolean result = analyzer.computeMergeImpact(pointFavorite);

        // Assert
        assertFalse(result); // Simplified implementation always returns false for points
    }
}
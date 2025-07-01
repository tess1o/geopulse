package org.github.tess1o.geopulse.timeline.service;

public enum FavoriteDeletionStrategy {
    REVERT_TO_GEOCODING,    // Show address instead of favorite name
    PRESERVE_HISTORICAL,    // Keep the favorite name even after deletion  
    ASK_USER               // Prompt user for decision (future feature)
}
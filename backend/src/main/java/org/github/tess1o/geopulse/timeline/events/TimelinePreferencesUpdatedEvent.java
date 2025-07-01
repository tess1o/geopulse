package org.github.tess1o.geopulse.timeline.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;

import java.util.UUID;

/**
 * Event fired when a user's timeline preferences are updated.
 * This triggers invalidation of all cached timeline data for the user
 * since preference changes affect timeline generation algorithms.
 */
@Getter
@AllArgsConstructor
@ToString
public class TimelinePreferencesUpdatedEvent {
    
    /**
     * ID of the user whose preferences were updated
     */
    private final UUID userId;
    
    /**
     * The updated timeline preferences
     */
    private final TimelinePreferences updatedPreferences;
    
    /**
     * Whether this was a reset to defaults (affects logging and metrics)
     */
    private final boolean wasResetToDefaults;
}
package org.github.tess1o.geopulse.streaming.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;

import java.util.UUID;

/**
 * Event fired when a user's travel classification preferences are updated.
 * This triggers recalculation of trip types for existing trips without
 * full timeline regeneration, providing better performance for classification-only changes.
 */
@Getter
@AllArgsConstructor
@ToString
public class TravelClassificationUpdatedEvent {
    
    /**
     * ID of the user whose travel classification preferences were updated
     */
    private final UUID userId;
    
    /**
     * The updated timeline preferences containing new classification settings
     */
    private final TimelinePreferences updatedPreferences;
    
    /**
     * Whether this was a reset to defaults (affects logging and metrics)
     */
    private final boolean wasResetToDefaults;
}
package org.github.tess1o.geopulse.streaming.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;

import java.util.UUID;

/**
 * Event fired when a user's timeline structural preferences are updated.
 * This triggers full timeline regeneration as structural changes (like staypoint detection,
 * merging parameters, path simplification, etc.) require reprocessing all GPS data.
 */
@Getter
@AllArgsConstructor
@ToString
public class TimelineStructureUpdatedEvent {
    
    /**
     * ID of the user whose timeline structure preferences were updated
     */
    private final UUID userId;
    
    /**
     * The updated timeline preferences containing new structural settings
     */
    private final TimelinePreferences updatedPreferences;
    
    /**
     * Whether this was a reset to defaults (affects logging and metrics)
     */
    private final boolean wasResetToDefaults;
}
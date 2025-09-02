package org.github.tess1o.geopulse.user.model;

public enum TimelineStatus {
    IDLE,       // Normal operation, background jobs are allowed to run
    PROCESSING, // A background job is running
    REGENERATING // A full regeneration is in progress
}

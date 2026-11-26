package org.github.tess1o.geopulse.admin.deletion;

/**
 * The tables the deletion worker empties, in order, a batch at a time. Each one names its table, which rows belong to
 * the deleted user, and the column that identifies a row, so every batch is a plain
 * {@code DELETE ... WHERE key = ANY(ARRAY(SELECT key ... LIMIT n))}.
 * <p>
 * The order follows the foreign keys: rows that other rows of the user point at go last, so no batch waits on, or
 * fails because of, a row that is deleted later. Small tables and tables that cascade from {@code users} are left to
 * {@link #FINALIZE}.
 */
public enum UserDeletionPhase {
    TIMELINE_PATH_MATCHES(Stage.TIMELINE, "timeline_trip_path_matches", "user_id = :userId", "id"),
    TIMELINE_NOTES(Stage.TIMELINE, "timeline_notes", "user_id = :userId", "id"),
    /** Trip plans; their items, visit matches and collaborators cascade. */
    TRIPS(Stage.TIMELINE, "trips", "user_id = :userId", "id"),
    TIMELINE_LABELS(Stage.TIMELINE, "timeline_labels", "user_id = :userId", "id"),
    TIMELINE_EDITS(Stage.TIMELINE, "timeline_edits", "user_id = :userId", "id"),
    TIMELINE_TRIPS(Stage.TIMELINE, "timeline_trips", "user_id = :userId", "id"),
    TIMELINE_STAYS(Stage.TIMELINE, "timeline_stays", "user_id = :userId", "id"),
    TIMELINE_DATA_GAPS(Stage.TIMELINE, "timeline_data_gaps", "user_id = :userId", "id"),
    GEOFENCE_EVENTS_OWNED(Stage.LOCATION_DATA, "geofence_events", "owner_user_id = :userId", "id"),
    /** Events in other users' geofences about the deleted user. */
    GEOFENCE_EVENTS_AS_SUBJECT(Stage.LOCATION_DATA, "geofence_events", "subject_user_id = :userId", "id"),
    WEATHER_SAMPLES(Stage.LOCATION_DATA, "weather_samples", "user_id = :userId", "id"),
    WEATHER_SAMPLE_TARGETS(Stage.LOCATION_DATA, "weather_sample_targets", "user_id = :userId", "id"),
    /** The key is (user_id, grid_m, cell_x, cell_y), so rows are picked by ctid. */
    COVERAGE_CELLS(Stage.LOCATION_DATA, "coverage_cells", "user_id = :userId", "ctid"),
    /** After stays and edits, which point at these rows. */
    REVERSE_GEOCODING(Stage.LOCATION_DATA, "reverse_geocoding_location", "user_id = :userId", "id"),
    /** The environment rows of each point cascade. */
    GPS_POINTS(Stage.GPS_POINTS, "gps_points", "user_id = :userId", "id"),
    /** Sweeps what slipped in after its phase, deletes the small tables and the user row. */
    FINALIZE(Stage.FINALIZING, null, null, null);

    /** The coarse progress an admin sees. */
    public enum Stage {
        WAITING, TIMELINE, LOCATION_DATA, GPS_POINTS, FINALIZING
    }

    private final Stage stage;
    private final String table;
    private final String userRows;
    private final String key;

    UserDeletionPhase(Stage stage, String table, String userRows, String key) {
        this.stage = stage;
        this.table = table;
        this.userRows = userRows;
        this.key = key;
    }

    public static UserDeletionPhase first() {
        return values()[0];
    }

    public UserDeletionPhase next() {
        return values()[ordinal() + 1];
    }

    public Stage stage() {
        return stage;
    }

    /** Deletes up to {@code :limit} of the user's rows. Parameters: {@code :userId}, {@code :limit}. */
    public String batchSql() {
        requireTable();
        // = ANY(ARRAY(...)) rather than IN (...): it lets ctid keys use a TID scan instead of a join.
        return "DELETE FROM " + table + " WHERE " + key + " = ANY(ARRAY(SELECT " + key + " FROM " + table
                + " WHERE " + userRows + " LIMIT :limit))";
    }

    /** Deletes all of the user's rows that are left. Parameter: {@code :userId}. */
    public String sweepSql() {
        requireTable();
        return "DELETE FROM " + table + " WHERE " + userRows;
    }

    public boolean deletesRows() {
        return table != null;
    }

    private void requireTable() {
        if (table == null) {
            throw new IllegalStateException(name() + " does not delete a table in batches");
        }
    }
}

-- Consolidate per-column user preferences into two JSONB documents so adding a preference
-- no longer needs a column, a migration, and a hand-written copy in every DTO.
--
--   ui_preferences               - app-wide UI settings (units, date/time format, language, landing page)
--   timeline_display_preferences - Timeline map rendering settings (never affect timeline generation)
--
-- Only values that are actually set are stored; absent keys resolve to application defaults at read time.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS ui_preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS timeline_display_preferences JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE users
SET ui_preferences = jsonb_strip_nulls(jsonb_build_object(
        'distanceUnit', distance_unit,
        'temperatureUnit', temperature_unit,
        'dateFormat', NULLIF(date_format, ''),
        'timeFormat', NULLIF(time_format, ''),
        'language', NULLIF(language, ''),
        'defaultRedirectUrl', NULLIF(default_redirect_url, '')
    )),
    timeline_display_preferences = jsonb_strip_nulls(jsonb_build_object(
        'customMapTileUrl', NULLIF(custom_map_tile_url, ''),
        'customMapStyleUrl', NULLIF(custom_map_style_url, ''),
        'mapRenderMode', map_render_mode,
        'pathSimplificationEnabled', timeline_display_path_simplification_enabled,
        'pathSimplificationTolerance', timeline_display_path_simplification_tolerance,
        'pathMaxPoints', timeline_display_path_max_points,
        'pathAdaptiveSimplification', timeline_display_path_adaptive_simplification,
        'defaultDateRangePreset', NULLIF(default_date_range_preset, ''),
        'showCurrentLocationTelemetry', timeline_display_show_current_location_telemetry,
        'autoShowTripReplayControls', timeline_display_auto_show_trip_replay_controls,
        'enable3dBuildingsByDefault', timeline_display_enable_3d_buildings_by_default,
        'mapMatchingEnabled', timeline_display_map_matching_enabled,
        'mapMatchingExcludedMovementTypes', NULLIF(timeline_display_map_matching_excluded_movement_types, '[]'::jsonb),
        'defaultPathColor', timeline_display_default_path_color,
        'activePathColor', timeline_display_active_path_color
    ));

ALTER TABLE users
    DROP COLUMN distance_unit,
    DROP COLUMN temperature_unit,
    DROP COLUMN date_format,
    DROP COLUMN time_format,
    DROP COLUMN language,
    DROP COLUMN default_redirect_url,
    DROP COLUMN custom_map_tile_url,
    DROP COLUMN custom_map_style_url,
    DROP COLUMN map_render_mode,
    DROP COLUMN default_date_range_preset,
    DROP COLUMN timeline_display_path_simplification_enabled,
    DROP COLUMN timeline_display_path_simplification_tolerance,
    DROP COLUMN timeline_display_path_max_points,
    DROP COLUMN timeline_display_path_adaptive_simplification,
    DROP COLUMN timeline_display_show_current_location_telemetry,
    DROP COLUMN timeline_display_auto_show_trip_replay_controls,
    DROP COLUMN timeline_display_enable_3d_buildings_by_default,
    DROP COLUMN timeline_display_map_matching_enabled,
    DROP COLUMN timeline_display_map_matching_excluded_movement_types,
    DROP COLUMN timeline_display_default_path_color,
    DROP COLUMN timeline_display_active_path_color;

COMMENT ON COLUMN users.ui_preferences IS
    'App-wide UI preferences (distanceUnit, temperatureUnit, dateFormat, timeFormat, language, defaultRedirectUrl). Absent keys use application defaults.';
COMMENT ON COLUMN users.timeline_display_preferences IS
    'Display-only Timeline map preferences. Never affect timeline generation. Absent keys use application defaults.';

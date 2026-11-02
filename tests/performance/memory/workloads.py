"""Fixtures and streamed semantic snapshots; all large work happens outside the backend."""

import json

USER_ID = "bd75cb76-7e44-4d7a-8a97-88735957e760"
EMAIL = "memory-benchmark@example.invalid"
PASSWORD = "timelinepass"
PASSWORD_HASH = "$2a$12$pe7F7dhlJC3OE7gp9lqiW.i2H/er3W0U7G357gqsj3pQJHrXifZCK"
DATASETS = ("stay", "trip", "events", "merges", "dense-altitude")
RECORDED = ("recorded-627", "recorded-631", "recorded-632")
STAT_FIELDS = (
    "avg_gps_speed", "max_gps_speed", "speed_variance",
    "altitude_sustained_height_meters", "altitude_vertical_speed_p90",
    "water_distance_meters", "water_distance_ratio", "longest_water_segment_meters",
)


def seed_sql(dataset, count, cache):
    if dataset not in DATASETS or count < 2 or cache not in ("hit", "miss"):
        raise ValueError("Invalid synthetic fixture")
    preferences = json.dumps({
        "staypointRadiusMeters": 50, "staypointMinDurationMinutes": 7,
        "isMergeEnabled": dataset == "merges", "mergeMaxDistanceMeters": 10000,
        "mergeMaxTimeGapMinutes": 30, "boatEnabled": False,
        "flightEnabled": True, "flightAltitudeDetectionEnabled": True,
    })
    # Every duplicate timestamp has different coordinates, preserving the GPS uniqueness constraint.
    expressions = {
        "stay": ("50 + sin(i) * 0.000001", "30 + cos(i) * 0.000001", "i", "0", "280"),
        "trip": ("50 + sin(i * 0.01) * 0.01", "30 + cos(i * 0.01) * 0.01", "i", "36", "280"),
        "events": (
            "50 + CASE WHEN i % 100 < 30 THEN 0 WHEN i % 100 >= 70 THEN 0.01 ELSE (i % 100 - 30) * 0.00025 END",
            "30 + CASE WHEN i % 100 < 30 THEN 0 WHEN i % 100 >= 70 THEN 0.02 ELSE (i % 100 - 30) * 0.0005 END",
            "i * 30", "CASE WHEN i % 100 BETWEEN 30 AND 69 THEN 36 ELSE 0 END", "280"),
        "merges": (
            "50 + CASE WHEN i % 100 BETWEEN 40 AND 59 THEN sin((i % 100 - 40) * pi() / 20) * 0.001 ELSE 0 END",
            "30", "i * 30", "CASE WHEN i % 100 BETWEEN 40 AND 59 THEN 10 ELSE 0 END", "280"),
        "dense-altitude": (
            "50 + sin(i * 0.01) * 0.01", "30 + cos(i * 0.01) * 0.01",
            "(i / 8) * 0.01", "90", "280 + 1500 * (1 + sin(i * 0.0001))"),
    }
    lat, lon, seconds, speed, altitude = expressions[dataset]
    cache_sql = ""
    if cache == "hit":
        cache_sql = """
        INSERT INTO reverse_geocoding_location
            (id, request_coordinates, result_coordinates, display_name, provider_name, created_at,
             last_accessed_at, city, country)
        VALUES
            (nextval('reverse_geocoding_location_seq'), ST_SetSRID(ST_MakePoint(30,50),4326),
             ST_SetSRID(ST_MakePoint(30,50),4326), 'Benchmark 50.00,30.00', 'nominatim', now(),now(),'Benchmark','Test'),
            (nextval('reverse_geocoding_location_seq'), ST_SetSRID(ST_MakePoint(30.02,50.01),4326),
             ST_SetSRID(ST_MakePoint(30.02,50.01),4326), 'Benchmark 50.01,30.02', 'nominatim', now(),now(),'Benchmark','Test');
        """
    return f"""
        UPDATE timeline_regeneration_campaigns SET status='CANCELLED';
        INSERT INTO users (id,email,emailverified,password_hash,full_name,is_active,role,timezone,
            created_at,updated_at,timeline_status,timeline_preferences)
        VALUES ('{USER_ID}','{EMAIL}',true,'{PASSWORD_HASH}','Memory benchmark',true,'ADMIN','UTC',
            now(),now(),'IDLE','{preferences}'::jsonb);
        INSERT INTO gps_points (user_id,coordinates,timestamp,accuracy,velocity,altitude,source_type,created_at)
        SELECT '{USER_ID}'::uuid, ST_SetSRID(ST_MakePoint({lon},{lat}),4326),
            '2010-01-01T00:00:00Z'::timestamptz + ({seconds}) * interval '1 second',
            5, {speed}, {altitude}, 'OWNTRACKS', '2010-01-01T00:00:00Z'::timestamptz
        FROM generate_series(0,{count - 1}) AS points(i);
        {cache_sql}
        ANALYZE gps_points;
    """


def recorded_user_sql():
    return f"""
        UPDATE timeline_regeneration_campaigns SET status='CANCELLED';
        INSERT INTO users (id,email,emailverified,password_hash,full_name,is_active,role,timezone,
            created_at,updated_at,timeline_status)
        VALUES ('{USER_ID}','{EMAIL}',true,'{PASSWORD_HASH}','Memory benchmark',true,'ADMIN','UTC',
            now(),now(),'IDLE');
    """


def corrections_sql():
    """A durable visit-name correction and an attached note for recorded-data rebuilds."""
    return f"""
        INSERT INTO timeline_edits (user_id,seq,kind,anchor_start,anchor_end,affected_from,affected_to,payload)
        SELECT user_id,1,'SET_VISIT_NAME',timestamp,timestamp + stay_duration * interval '1 second',
            timestamp,timestamp + stay_duration * interval '1 second',
            '{{"visitName":"Benchmark corrected visit"}}'::jsonb
        FROM timeline_stays WHERE user_id='{USER_ID}' AND stay_duration > 0 ORDER BY timestamp LIMIT 1;
        INSERT INTO timeline_notes (user_id,title,content_markdown,event_time,anchor_type,stay_id,
            source_item_start_time,source_item_duration_seconds,source_start_latitude,source_start_longitude,
            location,location_source,created_at,updated_at)
        SELECT user_id,'Benchmark note','This attachment must survive regeneration.',timestamp,'STAY',id,
            timestamp,stay_duration,ST_Y(location),ST_X(location),location,'DERIVED_STAY',now(),now()
        FROM timeline_stays WHERE user_id='{USER_ID}' ORDER BY timestamp LIMIT 1;
    """


def snapshot_sql():
    # psql's cursor streams unescaped JSON lines to the host without a backend HTTP export.
    removed = " - 'id' - 'user_id' - 'created_at' - 'last_updated' - 'updated_at'"
    stats = ",".join(f"'{name}',to_jsonb(t)->'{name}'" for name in STAT_FIELDS)
    strip_stats = "".join(f" - '{name}'" for name in STAT_FIELDS)
    return f"""
    \\set FETCH_COUNT 1000
      WITH latest AS (SELECT max(timestamp) AS ts FROM gps_points WHERE user_id='{USER_ID}'),
      rows AS (
        SELECT 'stay' AS kind, s.timestamp AS ts,
          (to_jsonb(s){removed} - 'favorite_id' - 'geocoding_id') || jsonb_build_object(
            'favorite',f.name,'geocoding',g.display_name) AS exact,
          '{{}}'::jsonb AS stats
        FROM timeline_stays s LEFT JOIN favorite_locations f ON f.id=s.favorite_id
          LEFT JOIN reverse_geocoding_location g ON g.id=s.geocoding_id WHERE s.user_id='{USER_ID}'
        UNION ALL
        SELECT 'trip',t.timestamp,(to_jsonb(t){removed}{strip_stats}),jsonb_build_object({stats})
        FROM timeline_trips t WHERE t.user_id='{USER_ID}'
        UNION ALL
        SELECT 'gap',g.start_time,CASE WHEN g.start_time=latest.ts
          THEN (to_jsonb(g){removed} - 'end_time' - 'duration_seconds') || '{{"ongoing":true}}'::jsonb
          ELSE to_jsonb(g){removed} END,'{{}}'::jsonb
        FROM timeline_data_gaps g CROSS JOIN latest WHERE g.user_id='{USER_ID}'
        UNION ALL
        SELECT 'note',n.event_time,(to_jsonb(n){removed} - 'stay_id' - 'trip_id') || jsonb_build_object(
          'stay_start',s.timestamp,'trip_start',t.timestamp),'{{}}'::jsonb
        FROM timeline_notes n LEFT JOIN timeline_stays s ON s.id=n.stay_id
          LEFT JOIN timeline_trips t ON t.id=n.trip_id WHERE n.user_id='{USER_ID}' AND n.deleted_at IS NULL
        UNION ALL
        SELECT 'edit',e.anchor_start,(to_jsonb(e){removed} - 'superseded_by' - 'place_favorite_id' - 'place_geocoding_id')
          || jsonb_build_object('applied_type',a.target_type,'target_start',COALESCE(s.timestamp,t.timestamp,g.start_time)),
          '{{}}'::jsonb
        FROM timeline_edits e LEFT JOIN timeline_edit_applications a ON a.edit_id=e.id
          LEFT JOIN timeline_stays s ON a.target_type='STAY' AND s.id=a.target_id
          LEFT JOIN timeline_trips t ON a.target_type='TRIP' AND t.id=a.target_id
          LEFT JOIN timeline_data_gaps g ON a.target_type='DATA_GAP' AND g.id=a.target_id
        WHERE e.user_id='{USER_ID}'
      )
      SELECT jsonb_build_object('kind',kind,'exact',exact,'stats',stats)::text
      FROM rows ORDER BY ts,kind,exact::text
    ;
    """

package org.github.tess1o.geopulse.shared.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized REST path prefixes for namespaced API roots.
 *
 * <p>Only prefixes that repeat across several resource classes live here; leaf
 * method paths stay literal on the resource itself.</p>
 */
public final class ApiPaths {

    public static final String GPS = "/gps";
    public static final String GPS_POINTS = GPS + "/points";
    public static final String GPS_SOURCES = GPS + "/sources";
    public static final String GPS_INGEST = GPS + "/ingest";

    public static final String INTEGRATIONS = "/integrations";
    public static final String TRIP_PLANNING = "/trip-planning";
    public static final String ADMIN_BACKUPS = "/admin/backups";
    public static final String ADMIN_SETTINGS_BACKUPS = ADMIN_BACKUPS + "/settings";

    private static final String API_V1 = "/api/v1";

    /**
     * Absolute legacy ingestion paths mapped to their canonical {@code /api/v1/gps/ingest/*} targets.
     *
     * <p>The historical {@code /api/*} device endpoints are retained so already deployed clients
     * keep working. The {@link org.github.tess1o.geopulse.gps.integrations.LegacyIngestionRewriter}
     * uses this map to reroute the requests and emit a deprecation warning.</p>
     */
    public static final Map<String, String> LEGACY_INGESTION_ALIASES = buildLegacyIngestionAliases();

    private ApiPaths() {
    }

    private static Map<String, String> buildLegacyIngestionAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();

        for (String provider : new String[]{
                "owntracks", "overland", "traccar", "gpslogger", "colota"}) {
            aliases.put("/api/" + provider, API_V1 + GPS_INGEST + "/" + provider);
        }

        // The legacy device URL has no hyphen; the canonical ingest segment does.
        aliases.put("/api/homeassistant", API_V1 + GPS_INGEST + "/home-assistant");

        String dawarichTarget = API_V1 + GPS_INGEST + "/dawarich";
        aliases.put("/api/dawarich/api/v1/health", dawarichTarget + "/health");
        aliases.put("/api/dawarich/api/v1/points", dawarichTarget + "/points");
        aliases.put("/api/dawarich/api/v1/stats", dawarichTarget + "/stats");

        return Map.copyOf(aliases);
    }
}

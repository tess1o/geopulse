package org.github.tess1o.geopulse.shared.openapi;

/**
 * OpenAPI tag names used by REST resources. Every tag must also be registered in {@link ApiTagCatalog},
 * which owns tag descriptions and the documentation groups; the OpenAPI filter fails the build otherwise.
 */
public final class ApiTags {

    // Authentication
    public static final String SESSIONS = "Sessions";
    public static final String OIDC_SIGN_IN = "OIDC Sign-in";
    public static final String MOBILE_SIGN_IN = "Mobile Sign-in";
    public static final String REGISTRATION = "Registration";
    public static final String API_TOKENS = "API Tokens";

    // Location data
    public static final String GPS_TRACKER_INGEST = "GPS Tracker Ingest";
    public static final String GPS_POINTS = "GPS Points";
    public static final String GPS_SOURCES = "GPS Sources";
    public static final String IMPORT = "Import";
    public static final String EXPORT = "Export";

    // Timeline and places
    public static final String TIMELINE = "Timeline";
    public static final String TIMELINE_CORRECTIONS = "Timeline Corrections";
    public static final String TIMELINE_LABELS = "Timeline Labels";
    public static final String PLACES = "Places";
    public static final String FAVORITES = "Favorites";
    public static final String GEOCODING = "Geocoding";
    public static final String NOTES = "Notes";

    // Insights
    public static final String STATISTICS = "Statistics";
    public static final String DIGESTS = "Digests";
    public static final String JOURNEY_INSIGHTS = "Journey Insights";
    public static final String LOCATION_ANALYTICS = "Location Analytics";
    public static final String COVERAGE = "Coverage";

    // Trips
    public static final String TRIPS = "Trips";
    public static final String TRIP_PLANNING = "Trip Planning";

    // Friends and sharing
    public static final String FRIENDS = "Friends";
    public static final String SHARE_LINKS = "Share Links";
    public static final String SHARED_LINK_VIEWER = "Shared Link Viewer";

    // Alerts
    public static final String GEOFENCES = "Geofences";
    public static final String NOTIFICATIONS = "Notifications";

    // Integrations
    public static final String IMMICH = "Immich";
    public static final String MEMOS = "Memos";
    public static final String AI_ASSISTANT = "AI Assistant";
    public static final String PLACES_TO_VISIT = "Places to Visit";
    public static final String WEATHER = "Weather";

    // Account and app
    public static final String PROFILE = "Profile & Preferences";
    public static final String HOME = "Home";
    public static final String SYSTEM = "System";

    // Administration
    public static final String ADMIN_USERS = "Admin: Users";
    public static final String ADMIN_INVITATIONS = "Admin: Invitations";
    public static final String ADMIN_API_TOKENS = "Admin: API Tokens";
    public static final String ADMIN_AUDIT_LOGS = "Admin: Audit Logs";
    public static final String ADMIN_BACKUPS = "Admin: Backups";
    public static final String ADMIN_SYSTEM_SETTINGS = "Admin: System Settings";
    public static final String ADMIN_OIDC_PROVIDERS = "Admin: OIDC Providers";
    public static final String ADMIN_GEOCODING_PROVIDERS = "Admin: Geocoding Providers";
    public static final String ADMIN_TIMELINE_REGENERATION = "Admin: Timeline Regeneration";
    public static final String ADMIN_WEATHER = "Admin: Weather";
    public static final String ADMIN_DASHBOARD = "Admin: Dashboard";

    private ApiTags() {
    }
}
